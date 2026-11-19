package reports

import (
	"axoo-server/internal/models"
	"errors"
	"strings"

	"github.com/google/uuid"
)

type Service struct {
	repo reportRepository
}

type reportRepository interface {
	Create(report *models.Report) (bool, error)
	TargetExists(targetType, targetID string) bool
}

func NewService(repo reportRepository) *Service {
	return &Service{repo: repo}
}

func (s *Service) Report(reporterID, targetType, targetID, reason string) (bool, error) {
	if targetType != "post" && targetType != "comment" && targetType != "user" {
		return false, errors.New("target_type non valido")
	}
	if _, err := uuid.Parse(targetID); err != nil || !s.repo.TargetExists(targetType, targetID) {
		return false, errors.New("contenuto non trovato")
	}
	reason = strings.TrimSpace(reason)
	if !validReportReasons[reason] {
		return false, errors.New("motivo della segnalazione non valido")
	}

	report := &models.Report{
		ReporterID: reporterID,
		TargetType: targetType,
		TargetID:   targetID,
		Reason:     reason,
	}

	created, err := s.repo.Create(report)
	if err == nil && created {
		go notifyReport(report)
	}
	return created, err
}

var validReportReasons = map[string]bool{
	"spam": true, "harassment": true, "hate": true,
	"sexual_content": true, "violence": true,
	"personal_information": true, "other": true,
}
