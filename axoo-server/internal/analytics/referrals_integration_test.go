package analytics

import (
	"net/url"
	"os"
	"testing"
	"time"

	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func analyticsTestTransaction(t *testing.T) *gorm.DB {
	t.Helper()
	raw := os.Getenv("AXOO_ANALYTICS_TEST_DB_URL")
	if raw == "" {
		t.Skip("set AXOO_ANALYTICS_TEST_DB_URL to a local isolated PostgreSQL database")
	}
	dsn, err := url.Parse(raw)
	if err != nil || (dsn.Hostname() != "localhost" && dsn.Hostname() != "127.0.0.1") {
		t.Fatal("analytics fixtures require a local PostgreSQL database")
	}
	db, err := gorm.Open(postgres.Open(raw), &gorm.Config{Logger: logger.Default.LogMode(logger.Silent)})
	if err != nil {
		t.Fatal(err)
	}
	pool, err := db.DB()
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { pool.Close() })
	tx := db.Begin()
	if tx.Error != nil {
		t.Fatal(tx.Error)
	}
	t.Cleanup(func() { tx.Rollback() })
	for _, statement := range []string{
		`CREATE TEMP TABLE users (id text PRIMARY KEY, university_id text, created_at timestamptz DEFAULT NOW(), email_hash text DEFAULT 'verified', is_admin boolean DEFAULT false, is_alpha_tester boolean DEFAULT false) ON COMMIT DROP`,
		`CREATE TEMP TABLE universities (id text, name text) ON COMMIT DROP`,
		`CREATE TEMP TABLE user_daily_activity (user_id text, university_id text, activity_date date, posts_created int DEFAULT 0, comments_created int DEFAULT 0, votes_changed int DEFAULT 0) ON COMMIT DROP`,
		`CREATE TEMP TABLE referrals (inviter_id text, invitee_id text PRIMARY KEY, created_at timestamptz, is_test boolean DEFAULT false) ON COMMIT DROP`,
		`CREATE TEMP TABLE user_emoji_packs (user_id text, pack_id text, granted_at timestamptz, PRIMARY KEY(user_id,pack_id)) ON COMMIT DROP`,
	} {
		if err := tx.Exec(statement).Error; err != nil {
			t.Fatal(err)
		}
	}
	return tx
}

func TestReferralMetricsDatabase(t *testing.T) {
	tx := analyticsTestTransaction(t)
	exec := func(sql string, args ...any) {
		t.Helper()
		if err := tx.Exec(sql, args...).Error; err != nil {
			t.Fatal(err)
		}
	}
	for _, statement := range []string{
		`INSERT INTO universities VALUES ('a','Campus A'),('b','Campus B')`,
		`INSERT INTO users (id,university_id) VALUES ('owner-a','a'),('owner-b','b'),('partial','a'),('mixed','a'),('alpha','a'),('admin','a'),('desk','a'),('seed','a'),('unearned','a')`,
		`UPDATE users SET is_alpha_tester=TRUE WHERE id='alpha'`,
		`UPDATE users SET is_admin=TRUE WHERE id='admin'`,
		`UPDATE users SET email_hash='contentdesk-test' WHERE id='desk'`,
		`UPDATE users SET email_hash='seed-network-test' WHERE id='seed'`,
	} {
		exec(statement)
	}
	invite := func(owner, invitee, campus, timestamp string, test bool) {
		t.Helper()
		exec("INSERT INTO users (id,university_id) VALUES (?,?) ON CONFLICT DO NOTHING", invitee, campus)
		exec("INSERT INTO referrals(inviter_id,invitee_id,created_at,is_test) VALUES (?,?,?,?)", owner, invitee, timestamp, test)
	}
	invite("owner-a", "a-old", "a", "2026-09-01T10:00:00Z", false)
	invite("owner-a", "a-week", "a", "2026-10-16T22:00:00Z", false)     // Oct 17 in Rome, inside seven days.
	invite("owner-a", "a-midnight", "a", "2026-10-21T22:00:00Z", false) // Oct 22 in Rome.
	invite("owner-a", "a-today", "a", "2026-10-22T08:00:00Z", false)
	invite("owner-b", "b-cross-campus", "a", "2026-10-01T10:00:00Z", false)
	invite("owner-b", "b-one", "b", "2026-10-01T10:00:00Z", false)
	invite("owner-b", "b-two", "b", "2026-10-01T10:00:00Z", false)
	invite("partial", "partial-one", "a", "2026-10-22T08:00:00Z", false)
	invite("mixed", "mixed-real", "b", "2026-10-22T08:00:00Z", false)
	for _, excluded := range []string{"alpha", "admin", "desk", "seed"} {
		invite("mixed", excluded, "a", "2026-10-22T08:00:00Z", false)
		invite(excluded, excluded+"-invitee", "a", "2026-10-22T08:00:00Z", false)
	}
	invite("mixed", "test-invite", "a", "2026-10-22T08:00:00Z", true)
	for _, owner := range []string{"owner-a", "partial", "mixed", "alpha", "unearned"} {
		exec("INSERT INTO user_emoji_packs VALUES (?,'amici-di-axoo','2026-10-21T22:00:00Z')", owner)
	}
	exec("INSERT INTO user_emoji_packs VALUES ('owner-b','amici-di-axoo','2026-10-01T10:00:00Z')")
	exec("INSERT INTO user_emoji_packs VALUES ('owner-a','other-pack','2026-10-22T08:00:00Z')")
	repo := NewRepository(tx)
	repo.now = func() time.Time { return time.Date(2026, 10, 22, 12, 0, 0, 0, time.UTC) }
	for _, test := range []struct {
		name   string
		days   int
		campus string
		want   ReferralSummary
	}{
		{"today", 1, "", ReferralSummary{TotalInviters: 4, PeriodInviters: 3, TotalInvitees: 9, PeriodInvitees: 4, TotalUnlockedUsers: 4, PeriodUnlockedUsers: 3}},
		{"week", 7, "", ReferralSummary{TotalInviters: 4, PeriodInviters: 3, TotalInvitees: 9, PeriodInvitees: 5, TotalUnlockedUsers: 4, PeriodUnlockedUsers: 3}},
		{"month", 30, "", ReferralSummary{TotalInviters: 4, PeriodInviters: 4, TotalInvitees: 9, PeriodInvitees: 8, TotalUnlockedUsers: 4, PeriodUnlockedUsers: 4}},
		{"campus a", 7, "a", ReferralSummary{TotalInviters: 3, PeriodInviters: 3, TotalInvitees: 6, PeriodInvitees: 4, TotalUnlockedUsers: 3, PeriodUnlockedUsers: 3}},
		{"campus b", 7, "b", ReferralSummary{TotalInviters: 1, PeriodInviters: 0, TotalInvitees: 3, PeriodInvitees: 1, TotalUnlockedUsers: 1, PeriodUnlockedUsers: 0}},
		{"empty campus", 7, "empty", ReferralSummary{}},
	} {
		t.Run(test.name, func(t *testing.T) {
			result, err := NewService(repo).Overview(test.days, test.campus)
			if err != nil {
				t.Fatal(err)
			}
			if result.Referrals != test.want {
				t.Fatalf("got %+v, want %+v", result.Referrals, test.want)
			}
		})
	}
	exec("DELETE FROM user_emoji_packs")
	exec("DELETE FROM referrals")
	empty, err := repo.Overview(7, "")
	if err != nil || empty == nil || empty.Referrals != (ReferralSummary{}) {
		t.Fatal("empty referral data must return zero counts", empty, err)
	}
}
