package reports

import (
	"axoo-server/internal/models"
	"testing"
)

type profileReportRepository struct{ saved *models.Report }

func (r *profileReportRepository) TargetExists(kind, id string) bool {
	return kind == "user" && id == "00000000-0000-0000-0000-000000000001"
}
func (r *profileReportRepository) Create(report *models.Report) (bool, error) {
	r.saved = report
	return false, nil
}

func TestProfileReportsUseTheExistingQueue(t *testing.T) {
	repo := &profileReportRepository{}
	service := NewService(repo)
	id := "00000000-0000-0000-0000-000000000001"
	if _, err := service.Report("viewer", "user", id, "harassment"); err != nil {
		t.Fatal(err)
	}
	if repo.saved == nil || repo.saved.TargetType != "user" || repo.saved.TargetID != id || repo.saved.ReporterID != "viewer" {
		t.Fatalf("profile report: %+v", repo.saved)
	}
	for _, input := range []struct{ kind, id, reason string }{
		{"user", "invalid-id", "harassment"},
		{"user", id, "invalid-reason"},
		{"invalid-kind", id, "harassment"},
	} {
		if _, err := service.Report("viewer", input.kind, input.id, input.reason); err == nil {
			t.Fatal("invalid report accepted")
		}
	}
}
