package analytics

import (
	"errors"
	"log"
	"time"
)

type Action string

const (
	ActionPost    Action = "post"
	ActionComment Action = "comment"
	ActionVote    Action = "vote"
)

var (
	ErrInvalidRange = errors.New("intervallo non valido")
)

type Summary struct {
	NewActiveUsers    int64   `json:"new_active_users"`
	ReturningUsers    int64   `json:"returning_users"`
	ReturningToday    int64   `json:"returning_today"`
	PreviousUsers     int64   `json:"previous_users"`
	InactiveUsers     int64   `json:"inactive_users"`
	PassiveUsers      int64   `json:"passive_users"`
	RepeatUsers       int64   `json:"repeat_users"`
	ActiveDaysPerUser float64 `json:"active_days_per_user"`
	ActiveRate        float64 `json:"active_rate"`
	ActivationRate    float64 `json:"activation_rate"`
	ReturnRate        float64 `json:"return_rate"`
	TotalUsers        int64   `json:"total_users"`
	NewUsers          int64   `json:"new_users"`
	ActiveUsers       int64   `json:"active_users"`
	EngagedUsers      int64   `json:"engaged_users"`
	DAU               int64   `json:"dau"`
	WAU               int64   `json:"wau"`
	MAU               int64   `json:"mau"`
	PostsCreated      int64   `json:"posts_created"`
	CommentsCreated   int64   `json:"comments_created"`
	VotesChanged      int64   `json:"votes_changed"`
	TotalActions      int64   `json:"total_actions"`
	ParticipationRate float64 `json:"participation_rate"`
	ActionsPerEngaged float64 `json:"actions_per_engaged"`
	DAUMAU            float64 `json:"dau_mau"`
}

type DailyPoint struct {
	NewActiveUsers  int64  `json:"new_active_users"`
	ReturningUsers  int64  `json:"returning_users"`
	Date            string `json:"date"`
	ActiveUsers     int64  `json:"active_users"`
	EngagedUsers    int64  `json:"engaged_users"`
	PostsCreated    int64  `json:"posts_created"`
	CommentsCreated int64  `json:"comments_created"`
	VotesChanged    int64  `json:"votes_changed"`
}

type CampusSummary struct {
	ReturningUsers int64  `json:"returning_users"`
	UniversityID   string `json:"university_id"`
	UniversityName string `json:"university_name"`
	TotalUsers     int64  `json:"total_users"`
	ActiveUsers    int64  `json:"active_users"`
	EngagedUsers   int64  `json:"engaged_users"`
	TotalActions   int64  `json:"total_actions"`
}

type Overview struct {
	GeneratedAt  time.Time       `json:"generated_at"`
	PeriodDays   int             `json:"period_days"`
	UniversityID string          `json:"university_id,omitempty"`
	Summary      Summary         `json:"summary"`
	Referrals    ReferralSummary `json:"referrals"`
	Daily        []DailyPoint    `json:"daily"`
	Campuses     []CampusSummary `json:"campuses"`
}

type ReferralSummary struct {
	TotalInviters       int64 `json:"total_inviters"`
	PeriodInviters      int64 `json:"period_inviters"`
	TotalInvitees       int64 `json:"total_invitees"`
	PeriodInvitees      int64 `json:"period_invitees"`
	TotalUnlockedUsers  int64 `json:"total_unlocked_users"`
	PeriodUnlockedUsers int64 `json:"period_unlocked_users"`
}

type analyticsRepository interface {
	RecordOpen(userID string) error
	RecordAction(userID string, action Action) error
	Overview(days int, universityID string) (*Overview, error)
}

type ActionRecorder interface {
	RecordAction(userID string, action Action) error
}

type Service struct {
	repo analyticsRepository
}

func NewService(repo analyticsRepository) *Service {
	return &Service{repo: repo}
}

func (s *Service) RecordOpen(userID string) error {
	return s.repo.RecordOpen(userID)
}

func (s *Service) RecordAction(userID string, action Action) error {
	return s.repo.RecordAction(userID, action)
}

// Overview is exposed only by the private Content Desk, after session validation.
func (s *Service) Overview(days int, universityID string) (*Overview, error) {
	if days != 1 && days != 7 && days != 30 {
		return nil, ErrInvalidRange
	}
	result, err := s.repo.Overview(days, universityID)
	if err != nil {
		return nil, err
	}
	result.Summary.TotalActions = result.Summary.PostsCreated + result.Summary.CommentsCreated + result.Summary.VotesChanged
	result.Summary.ParticipationRate = percentage(result.Summary.EngagedUsers, result.Summary.ActiveUsers)
	result.Summary.ActionsPerEngaged = division(result.Summary.TotalActions, result.Summary.EngagedUsers)
	result.Summary.DAUMAU = percentage(result.Summary.DAU, result.Summary.MAU)
	result.Summary.PreviousUsers = result.Summary.TotalUsers - result.Summary.NewUsers
	result.Summary.InactiveUsers = result.Summary.TotalUsers - result.Summary.ActiveUsers
	result.Summary.PassiveUsers = result.Summary.ActiveUsers - result.Summary.EngagedUsers
	result.Summary.ActiveRate = percentage(result.Summary.ActiveUsers, result.Summary.TotalUsers)
	result.Summary.ActivationRate = percentage(result.Summary.NewActiveUsers, result.Summary.NewUsers)
	result.Summary.ReturnRate = percentage(result.Summary.ReturningUsers, result.Summary.PreviousUsers)
	return result, nil
}

func RecordActionBestEffort(recorder ActionRecorder, userID string, action Action) {
	if recorder == nil {
		return
	}
	if err := recorder.RecordAction(userID, action); err != nil {
		log.Printf("analytics: registrazione azione %s fallita: %v", action, err)
	}
}

func percentage(value, total int64) float64 {
	return division(value, total) * 100
}

func division(value, total int64) float64 {
	if total <= 0 {
		return 0
	}
	return float64(value) / float64(total)
}
