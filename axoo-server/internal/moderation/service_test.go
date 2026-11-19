package moderation

import (
	"axoo-server/internal/models"
	"testing"
	"time"
)

type queueRepository struct {
	moderationRepository
	reports []models.Report
}

func (r queueRepository) ModeratorCityIDs(string) ([]string, error) { return []string{"bari"}, nil }
func (r queueRepository) ListOpenReports([]string, int) ([]models.Report, error) {
	return r.reports, nil
}
func (r queueRepository) FindTarget(kind, id string) (*Target, error) {
	return &Target{Type: kind, ID: id, AuthorID: "author"}, nil
}

func TestQueueGroupsReportsAndPrioritizesOldest(t *testing.T) {
	first := time.Date(2026, 10, 7, 8, 0, 0, 0, time.UTC)
	service := NewService(queueRepository{reports: []models.Report{
		{TargetType: "post", TargetID: "old", CreatedAt: first, Reason: "hate"},
		{TargetType: "post", TargetID: "popular", CreatedAt: first.Add(time.Hour), Reason: "spam"},
		{TargetType: "post", TargetID: "popular", CreatedAt: first.Add(2 * time.Hour), Reason: "spam"},
		{TargetType: "post", TargetID: "old", CreatedAt: first.Add(3 * time.Hour), Reason: "harassment"},
	}})
	items, err := service.Queue("moderator", "open")
	if err != nil {
		t.Fatal(err)
	}
	if len(items) != 2 || items[0].TargetID != "old" {
		t.Fatalf("unexpected queue: %+v", items)
	}
	old := items[0]
	if old.ReportCount != 2 || len(old.Reasons) != 2 || !old.FirstReportedAt.Equal(first) || !old.LastReportedAt.Equal(first.Add(3*time.Hour)) {
		t.Fatalf("unexpected report grouping: %+v", old)
	}
}

func TestProfileAccountActionSourceMustMatchTheAccount(t *testing.T) {
	id := "00000000-0000-0000-0000-000000000001"
	input := ActionInput{TargetType: "user", TargetID: id, SourceTargetType: "user", SourceTargetID: id}
	if err := validateSourceTarget(input); err != nil {
		t.Fatal(err)
	}
	input.SourceTargetID = "00000000-0000-0000-0000-000000000002"
	if err := validateSourceTarget(input); err == nil {
		t.Fatal("accepted an unrelated profile as the action source")
	}
}
