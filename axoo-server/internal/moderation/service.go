package moderation

import (
	"errors"
	"sort"
	"strings"
	"time"

	"axoo-server/internal/models"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

const (
	ActionHide      = "hide"
	ActionRestore   = "restore"
	ActionKeep      = "keep"
	ActionSuspend   = "suspend"
	ActionBan       = "ban"
	ActionUnsuspend = "unsuspend"
)

var ErrForbidden = errors.New("accesso riservato ai moderatori")

type moderationRepository interface {
	ModeratorCityIDs(userID string) ([]string, error)
	ListOpenReports(cityIDs []string, limit int) ([]models.Report, error)
	FindTarget(targetType, targetID string) (*Target, error)
	FindUserTarget(userID string) (*Target, error)
	FindActionSource(actionID string) (*Target, error)
	ReportsForAction(actionID string) ([]models.Report, error)
	Apply(moderatorID string, input ActionInput) error
	ListActions(cityIDs []string, limit int) ([]models.ModerationAction, error)
}

type Service struct {
	repo moderationRepository
}

func NewService(repo moderationRepository) *Service {
	return &Service{repo: repo}
}

type QueueItem struct {
	TargetType       string    `json:"target_type"`
	TargetID         string    `json:"target_id"`
	AuthorID         string    `json:"author_user_id"`
	AuthorNickname   string    `json:"author_nickname"`
	Body             string    `json:"body"`
	ReportCount      int       `json:"report_count"`
	Reasons          []string  `json:"reasons"`
	Action           string    `json:"action,omitempty"`
	IsHidden         bool      `json:"is_hidden"`
	IsDeleted        bool      `json:"is_deleted"`
	CreatedAt        time.Time `json:"created_at"`
	FirstReportedAt  time.Time `json:"first_reported_at"`
	LastReportedAt   time.Time `json:"last_reported_at"`
	DecisionReason   string    `json:"decision_reason,omitempty"`
	AuthorRestricted bool      `json:"author_restricted,omitempty"`
}

type ActionInput struct {
	Action           string `json:"action"`
	TargetType       string `json:"target_type"`
	TargetID         string `json:"target_id"`
	Reason           string `json:"reason"`
	DurationHours    int    `json:"duration_hours"`
	SourceTargetType string `json:"source_target_type"`
	SourceTargetID   string `json:"source_target_id"`
}

func (s *Service) Queue(moderatorID, status string) ([]QueueItem, error) {
	cityIDs, err := s.moderatorCityIDs(moderatorID)
	if err != nil {
		return nil, err
	}
	if status == "resolved" {
		return s.history(cityIDs)
	}
	reports, err := s.repo.ListOpenReports(cityIDs, 200)
	if err != nil {
		return nil, err
	}

	items := make([]QueueItem, 0)
	indices := make(map[string]int)
	for _, report := range reports {
		key := report.TargetType + ":" + report.TargetID
		if index, found := indices[key]; found {
			items[index].ReportCount++
			if report.CreatedAt.After(items[index].LastReportedAt) {
				items[index].LastReportedAt = report.CreatedAt
			}
			if report.Reason != "" && !contains(items[index].Reasons, report.Reason) {
				items[index].Reasons = append(items[index].Reasons, report.Reason)
			}
			continue
		}
		target, targetErr := s.repo.FindTarget(report.TargetType, report.TargetID)
		if errors.Is(targetErr, gorm.ErrRecordNotFound) {
			continue
		}
		if targetErr != nil {
			return nil, targetErr
		}
		reasons := make([]string, 0, 1)
		if report.Reason != "" {
			reasons = append(reasons, report.Reason)
		}
		indices[key] = len(items)
		items = append(items, QueueItem{
			TargetType: target.Type, TargetID: target.ID,
			AuthorID: target.AuthorID, AuthorNickname: target.AuthorNickname,
			Body: target.Body, ReportCount: 1, Reasons: reasons,
			IsHidden: target.IsHidden, IsDeleted: target.IsDeleted,
			CreatedAt: target.CreatedAt, FirstReportedAt: report.CreatedAt, LastReportedAt: report.CreatedAt,
		})
	}
	sort.SliceStable(items, func(i, j int) bool {
		return items[i].FirstReportedAt.Before(items[j].FirstReportedAt)
	})
	return items, nil
}

func (s *Service) Act(moderatorID string, input ActionInput) error {
	cityIDs, err := s.moderatorCityIDs(moderatorID)
	if err != nil {
		return err
	}
	input.Action = strings.TrimSpace(strings.ToLower(input.Action))
	input.TargetType = strings.TrimSpace(strings.ToLower(input.TargetType))
	input.Reason = strings.TrimSpace(input.Reason)
	if _, err := uuid.Parse(input.TargetID); err != nil {
		return errors.New("target_id non valido")
	}
	if input.TargetID == moderatorID && (input.Action == ActionSuspend || input.Action == ActionBan) {
		return errors.New("un moderatore non può sospendere il proprio account")
	}

	switch input.Action {
	case ActionKeep:
		if input.TargetType != "post" && input.TargetType != "comment" && input.TargetType != "user" {
			return errors.New("target_type non valido")
		}
	case ActionHide, ActionRestore:
		if input.TargetType != "post" && input.TargetType != "comment" {
			return errors.New("target_type non valido")
		}
	case ActionSuspend:
		if input.TargetType != "user" || (input.DurationHours != 24 && input.DurationHours != 168) {
			return errors.New("la sospensione deve durare 24 o 168 ore")
		}
		if err := validateSourceTarget(input); err != nil {
			return err
		}
	case ActionBan, ActionUnsuspend:
		if input.TargetType != "user" {
			return errors.New("target_type non valido")
		}
		if input.Action == ActionBan {
			if err := validateSourceTarget(input); err != nil {
				return err
			}
		}
	default:
		return errors.New("azione non valida")
	}
	if (input.Action == ActionHide || input.Action == ActionSuspend || input.Action == ActionBan) && input.Reason == "" {
		return errors.New("motivo obbligatorio")
	}
	if input.Reason != "" && !validDecisionReasons[input.Reason] {
		return errors.New("motivo non valido")
	}
	authorizationTargetType, authorizationTargetID := input.TargetType, input.TargetID
	if input.TargetType == "user" && input.SourceTargetID != "" {
		authorizationTargetType, authorizationTargetID = input.SourceTargetType, input.SourceTargetID
	}
	var target *Target
	var targetErr error
	if authorizationTargetType == "user" {
		target, targetErr = s.repo.FindUserTarget(authorizationTargetID)
	} else {
		target, targetErr = s.repo.FindTarget(authorizationTargetType, authorizationTargetID)
	}
	if targetErr != nil {
		return targetErr
	}
	if !contains(cityIDs, target.CityID) {
		return ErrForbidden
	}
	if input.TargetType == "user" && input.SourceTargetID != "" {
		if target.AuthorID != input.TargetID {
			return ErrForbidden
		}
		user, err := s.repo.FindUserTarget(input.TargetID)
		if err != nil {
			return err
		}
		if !contains(cityIDs, user.CityID) {
			return ErrForbidden
		}
	}
	return s.repo.Apply(moderatorID, input)
}

var validDecisionReasons = map[string]bool{
	"spam": true, "harassment": true, "hate": true,
	"sexual_content": true, "violence": true,
	"personal_information": true, "other": true,
}

func validateSourceTarget(input ActionInput) error {
	if (input.SourceTargetType != "post" && input.SourceTargetType != "comment" && input.SourceTargetType != "user") || input.SourceTargetID == "" {
		return errors.New("contenuto segnalato mancante")
	}
	if input.SourceTargetType == "user" && input.SourceTargetID != input.TargetID {
		return errors.New("profilo segnalato non valido")
	}
	if _, err := uuid.Parse(input.SourceTargetID); err != nil {
		return errors.New("contenuto segnalato non valido")
	}
	return nil
}

func (s *Service) Actions(moderatorID string) ([]models.ModerationAction, error) {
	cityIDs, err := s.moderatorCityIDs(moderatorID)
	if err != nil {
		return nil, err
	}
	return s.repo.ListActions(cityIDs, 100)
}

func (s *Service) moderatorCityIDs(userID string) ([]string, error) {
	cityIDs, err := s.repo.ModeratorCityIDs(userID)
	if err != nil {
		return nil, err
	}
	if len(cityIDs) == 0 {
		return nil, ErrForbidden
	}
	return cityIDs, nil
}

func (s *Service) history(cityIDs []string) ([]QueueItem, error) {
	actions, err := s.repo.ListActions(cityIDs, 200)
	if err != nil {
		return nil, err
	}
	items := make([]QueueItem, 0, len(actions))
	for _, action := range actions {
		var target *Target
		if action.TargetType == "user" {
			target, err = s.repo.FindActionSource(action.ID)
			if errors.Is(err, gorm.ErrRecordNotFound) {
				target, err = s.repo.FindUserTarget(action.TargetID)
			}
		} else {
			target, err = s.repo.FindTarget(action.TargetType, action.TargetID)
		}
		if errors.Is(err, gorm.ErrRecordNotFound) {
			continue
		}
		if err != nil {
			return nil, err
		}
		reports, reportErr := s.repo.ReportsForAction(action.ID)
		if reportErr != nil {
			return nil, reportErr
		}
		reasons := make([]string, 0)
		lastReportedAt := action.CreatedAt
		for _, report := range reports {
			if report.Reason != "" && !contains(reasons, report.Reason) {
				reasons = append(reasons, report.Reason)
			}
			if report.CreatedAt.After(lastReportedAt) {
				lastReportedAt = report.CreatedAt
			}
		}
		authorID := target.AuthorID
		authorNickname := target.AuthorNickname
		authorRestricted := false
		if action.TargetType == "user" {
			authorID = action.TargetID
			if userTarget, userErr := s.repo.FindUserTarget(action.TargetID); userErr == nil {
				authorNickname = userTarget.AuthorNickname
				authorRestricted = userTarget.IsRestricted
			}
		}
		items = append(items, QueueItem{
			TargetType: action.TargetType, TargetID: action.TargetID,
			AuthorID: authorID, AuthorNickname: authorNickname,
			Body: target.Body, ReportCount: len(reports), Reasons: reasons,
			Action:   action.Action,
			IsHidden: target.IsHidden, IsDeleted: target.IsDeleted,
			CreatedAt: target.CreatedAt, LastReportedAt: lastReportedAt,
			DecisionReason:   action.Reason,
			AuthorRestricted: authorRestricted,
		})
	}
	return items, nil
}

func contains(values []string, candidate string) bool {
	for _, value := range values {
		if value == candidate {
			return true
		}
	}
	return false
}
