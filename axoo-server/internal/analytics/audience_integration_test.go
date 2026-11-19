package analytics

import (
	"encoding/json"
	"testing"
	"time"
)

func TestAudienceCohortsDatabase(t *testing.T) {
	tx := analyticsTestTransaction(t)
	for _, statement := range []string{
		`INSERT INTO universities VALUES ('a','Campus A'),('b','Campus B')`,
		`INSERT INTO users (id,university_id,created_at) VALUES
		 ('old-active','a','2026-09-01T00:00:00Z'), ('old-inactive','a','2026-09-01T00:00:00Z'),
		 ('new-active','a','2026-10-18T22:00:00Z'), ('new-inactive','a','2026-10-19T08:00:00Z'),
		 ('recent','a','2026-10-15T08:00:00Z'), ('other-campus','b','2026-09-01T00:00:00Z')`,
		`INSERT INTO users (id,university_id,created_at,is_admin,is_alpha_tester) VALUES ('alpha','a','2026-09-01T00:00:00Z',false,true),('admin','a','2026-09-01T00:00:00Z',true,false)`,
		`INSERT INTO user_daily_activity (user_id,university_id,activity_date,posts_created) VALUES
		 ('old-active','a','2026-10-18',0), ('old-active','a','2026-10-19',1),
		 ('new-active','a','2026-10-19',0), ('recent','a','2026-10-15',0), ('recent','a','2026-10-19',0),
		 ('other-campus','b','2026-10-19',0), ('alpha','a','2026-10-19',5), ('admin','a','2026-10-19',5)`,
	} {
		if err := tx.Exec(statement).Error; err != nil {
			t.Fatal(err)
		}
	}
	repo := NewRepository(tx)
	repo.now = func() time.Time { return time.Date(2026, 10, 19, 12, 0, 0, 0, time.UTC) }
	service := NewService(repo)
	for _, days := range []int{1, 7, 30} {
		result, err := service.Overview(days, "")
		if err != nil {
			t.Fatal(err)
		}
		if len(result.Daily) != days {
			t.Fatalf("daily points = %d, want %d", len(result.Daily), days)
		}

		s := result.Summary
		var posts, comments, votes int64
		for _, day := range result.Daily {
			if _, err := time.Parse(time.DateOnly, day.Date); err != nil {
				t.Fatal(err)
			}
			if day.ActiveUsers > s.ActiveUsers || day.NewActiveUsers+day.ReturningUsers != day.ActiveUsers {
				t.Fatalf("inconsistent daily audience: %+v", day)
			}
			posts += day.PostsCreated
			comments += day.CommentsCreated
			votes += day.VotesChanged
		}
		if posts != s.PostsCreated || comments != s.CommentsCreated || votes != s.VotesChanged {
			t.Fatalf("daily values do not match summary for %d days", days)
		}
		if s.DAU > s.WAU || s.WAU > s.MAU || s.NewActiveUsers+s.ReturningUsers != s.ActiveUsers ||
			s.NewActiveUsers > s.NewUsers || s.ReturningUsers > s.PreviousUsers || s.ReturningToday > s.DAU || s.RepeatUsers > s.ActiveUsers {
			t.Fatalf("inconsistent audience summary: %+v", s)
		}
		if s.TotalUsers != 6 || s.ActiveUsers != 4 || s.ReturningToday != 3 || s.InactiveUsers != 2 || s.PassiveUsers != 3 || s.PostsCreated != 1 {
			t.Fatalf("%d days: incorrect counts/exclusions: %+v", days, s)
		}
		switch days {
		case 1:
			if s.NewUsers != 2 || s.NewActiveUsers != 1 || s.ReturningUsers != 3 || s.ReturnRate != 75 || s.ActivationRate != 50 || s.RepeatUsers != 0 || s.ActiveDaysPerUser != 1 {
				t.Fatalf("incorrect daily cohort: %+v", s)
			}
		case 7:
			if s.NewUsers != 3 || s.NewActiveUsers != 2 || s.ReturningUsers != 2 || s.RepeatUsers != 2 || s.ActiveDaysPerUser != 1.5 {
				t.Fatalf("incorrect weekly cohort: %+v", s)
			}
		}
		last := result.Daily[len(result.Daily)-1]
		if last.NewActiveUsers != 1 || last.ReturningUsers != 3 {
			t.Fatalf("incorrect Rome boundary or daily counts: %+v", last)
		}
	}
	filtered, err := service.Overview(7, "a")
	if err != nil {
		t.Fatal(err)
	}
	if filtered.Summary.TotalUsers != 5 || filtered.Summary.ActiveUsers != 3 || filtered.Summary.ReturningUsers != 1 || filtered.Summary.ReturningToday != 2 || len(filtered.Campuses) != 1 || filtered.Campuses[0].ReturningUsers != 1 {
		t.Fatalf("incorrect campus cohort: %+v", filtered)
	}

	empty, err := service.Overview(7, "no-campus")
	if err != nil {
		t.Fatal(err)
	}
	if empty.Summary.TotalUsers != 0 || empty.Summary.ActiveUsers != 0 {
		t.Fatal("empty campus has activity")
	}
	if campuses, err := json.Marshal(empty.Campuses); err != nil || string(campuses) != "[]" {
		t.Fatalf("empty campus list must be an array: %s (%v)", campuses, err)
	}
}
