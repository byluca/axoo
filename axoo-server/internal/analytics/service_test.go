package analytics

import (
	"encoding/json"
	"errors"
	"net/http/httptest"
	"testing"

	"github.com/gofiber/fiber/v2"
)

type stubRepository struct {
	result Overview
	calls  int
	opened string
}

func (r *stubRepository) RecordOpen(userID string) error          { r.opened = userID; return nil }
func (r *stubRepository) RecordAction(string, Action) error       { return nil }
func (r *stubRepository) Overview(int, string) (*Overview, error) { r.calls++; return &r.result, nil }

func TestOverviewMetricsAndPeriods(t *testing.T) {
	for _, days := range []int{1, 7, 30} {
		repo := &stubRepository{result: Overview{Summary: Summary{
			TotalUsers: 20, NewUsers: 5, NewActiveUsers: 2, ReturningUsers: 6,
			ActiveUsers: 8, EngagedUsers: 2, DAU: 3, MAU: 12,
			PostsCreated: 1, CommentsCreated: 2, VotesChanged: 3,
		}}}
		result, err := NewService(repo).Overview(days, "")
		if err != nil {
			t.Fatal(err)
		}
		s := result.Summary
		if s.TotalActions != 6 || s.ParticipationRate != 25 || s.ActionsPerEngaged != 3 || s.DAUMAU != 25 {
			t.Fatalf("incorrect derived metrics: %+v", s)
		}
		if s.PreviousUsers != 15 || s.InactiveUsers != 12 || s.PassiveUsers != 6 || s.ActiveRate != 40 || s.ActivationRate != 40 || s.ReturnRate != 40 {
			t.Fatalf("incorrect audience metrics: %+v", s)
		}
	}
	repo := &stubRepository{}
	if _, err := NewService(repo).Overview(2, ""); !errors.Is(err, ErrInvalidRange) || repo.calls != 0 {
		t.Fatalf("invalid period should fail before reading the database: %v", err)
	}
	result, err := NewService(repo).Overview(7, "")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := json.Marshal(result); err != nil {
		t.Fatalf("empty metrics must not produce NaN: %v", err)
	}
}

func TestMobileKeepsActivityRecordingWithoutStatisticsRoute(t *testing.T) {
	repo := &stubRepository{}
	app := fiber.New()
	app.Use(func(c *fiber.Ctx) error { c.Locals("user_id", "mobile-user"); return c.Next() })
	NewHandler(NewService(repo)).RegisterRoutes(app.Group("/api/v1"))
	response, err := app.Test(httptest.NewRequest("POST", "/api/v1/activity/open", nil))
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	if response.StatusCode != 204 || repo.opened != "mobile-user" {
		t.Fatalf("activity recording failed: %d", response.StatusCode)
	}
	response, err = app.Test(httptest.NewRequest("GET", "/api/v1/admin/analytics", nil))
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	if response.StatusCode != 404 || repo.calls != 0 {
		t.Fatalf("mobile statistics route is still exposed: %d", response.StatusCode)
	}
}
