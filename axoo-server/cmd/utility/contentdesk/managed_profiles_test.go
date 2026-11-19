package main

import (
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"strings"
	"testing"
	"time"

	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func TestBulkProfilesRejectInvalidRequests(t *testing.T) {
	d := &desk{sessionToken: "private"}
	for _, body := range []string{`{`, `{"count":0}`, `{"count":301}`, `{"count":5,"university_id":"invalid"}`} {
		w := httptest.NewRecorder()
		d.createProfiles(w, httptest.NewRequest("POST", "/api/profiles/bulk", strings.NewReader(body)))
		if w.Code != http.StatusBadRequest {
			t.Fatalf("%s: got %d", body, w.Code)
		}
	}
	w := httptest.NewRecorder()
	d.authorized(d.createProfiles)(w, httptest.NewRequest("POST", "/api/profiles/bulk", strings.NewReader(`{"count":5}`)))
	if w.Code != http.StatusUnauthorized {
		t.Fatalf("bulk creation exposed without session: %d", w.Code)
	}
}

func TestBulkProfilesDatabase(t *testing.T) {
	raw := os.Getenv("AXOO_CONTENT_DESK_TEST_DB_URL")
	if raw == "" {
		t.Skip("set AXOO_CONTENT_DESK_TEST_DB_URL to a local isolated PostgreSQL database")
	}
	dsn, err := url.Parse(raw)
	if err != nil || (dsn.Hostname() != "localhost" && dsn.Hostname() != "127.0.0.1") {
		t.Fatal("Content Desk fixtures require a local PostgreSQL database")
	}
	db, err := gorm.Open(postgres.Open(raw), &gorm.Config{Logger: logger.Default.LogMode(logger.Silent)})
	if err != nil {
		t.Fatal("cannot connect to database")
	}
	sqlDB, err := db.DB()
	if err != nil {
		t.Fatal(err)
	}
	defer sqlDB.Close()
	tx := db.Begin()
	if tx.Error != nil {
		t.Fatal(tx.Error)
	}
	defer tx.Rollback()
	// All writes target temporary tables and are discarded on rollback.
	for _, sql := range []string{
		`CREATE TEMP TABLE users (
			id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
			email_hash text UNIQUE NOT NULL,
			nickname text UNIQUE NOT NULL,
			university_id uuid NOT NULL,
			is_alpha_tester boolean NOT NULL DEFAULT false,
			is_admin boolean NOT NULL DEFAULT false,
			is_moderator boolean NOT NULL DEFAULT false,
			is_banned boolean NOT NULL DEFAULT false,
			is_profile_public boolean NOT NULL DEFAULT false,
			bio text NOT NULL DEFAULT '',
			bio_entities jsonb NOT NULL DEFAULT '[]',
			avatar_config jsonb,
			suspended_until timestamptz,
			suspension_reason text NOT NULL DEFAULT '',
			terms_version text NOT NULL DEFAULT '',
			terms_accepted_at timestamptz,
			created_at timestamptz NOT NULL DEFAULT now()
		) ON COMMIT DROP`,
		`CREATE TEMP TABLE universities (id uuid, name text) ON COMMIT DROP`,
		`INSERT INTO universities VALUES ('12345678-1234-4234-8234-123456789abc','Campus prova')`,
	} {
		if err := tx.Exec(sql).Error; err != nil {
			t.Fatal(err)
		}
	}
	m := managedProfiles{db: tx}
	seen := map[string]bool{}
	for range 2 {
		profiles, err := m.addProfiles(300, "12345678-1234-4234-8234-123456789abc", "")
		if err != nil {
			t.Fatal(err)
		}
		if len(profiles) != 300 {
			t.Fatalf("created %d profiles", len(profiles))
		}
		for _, p := range profiles {
			name := strings.ToLower(p.Nickname)
			if seen[name] || !nicknamePattern.MatchString(p.Nickname) || p.UniversityName != "Campus prova" {
				t.Fatalf("invalid generated profile: %+v", p)
			}
			seen[name] = true
		}
	}
	var count int64
	if err := tx.Table("users").Where("is_alpha_tester = TRUE AND is_admin = FALSE AND email_hash LIKE 'contentdesk-%'").Count(&count).Error; err != nil {
		t.Fatal(err)
	}
	if count != 600 {
		t.Fatalf("expected 600 excluded managed accounts, got %d", count)
	}
	if err := tx.Exec(`UPDATE users SET created_at = '2026-10-19T17:23:00Z' WHERE id IN (SELECT id FROM users ORDER BY id LIMIT 300)`).Error; err != nil {
		t.Fatal(err)
	}
	if err := tx.Exec(`UPDATE users SET created_at = '2026-10-18T00:00:00Z' WHERE created_at != '2026-10-19T17:23:00Z'`).Error; err != nil {
		t.Fatal(err)
	}
	since := time.Date(2026, 10, 19, 17, 23, 0, 0, time.UTC)
	if _, err := m.changeProfileNicknames(299, since, false); err == nil {
		t.Fatal("must refuse an unexpected number of targets")
	}
	changes, err := m.changeProfileNicknames(300, since, false)
	if err != nil {
		t.Fatal(err)
	}
	if len(changes) != 300 {
		t.Fatalf("renamed %d profiles", len(changes))
	}
	for _, change := range changes {
		if seen[strings.ToLower(change.Nickname)] || change.OldNickname == change.Nickname {
			t.Fatalf("reused old nickname: %+v", change)
		}
	}
	if err := tx.Table("users").Where("is_alpha_tester = TRUE AND is_admin = FALSE AND email_hash LIKE 'contentdesk-%'").Count(&count).Error; err != nil {
		t.Fatal(err)
	}
	if count != 600 {
		t.Fatalf("rename changed account count or flags: %d", count)
	}
}
