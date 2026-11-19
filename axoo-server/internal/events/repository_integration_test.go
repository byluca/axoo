package events

import (
	"axoo-server/internal/models"
	"errors"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/google/uuid"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
)

func TestEventsDatabase(t *testing.T) {
	dsn := os.Getenv("AXOO_EVENTS_TEST_DB_URL")
	if dsn == "" {
		t.Skip("set AXOO_EVENTS_TEST_DB_URL for isolated PostgreSQL tests")
	}
	db, err := gorm.Open(postgres.New(postgres.Config{DSN: dsn, PreferSimpleProtocol: true}), &gorm.Config{})
	if err != nil {
		t.Fatal(err)
	}
	pool, err := db.DB()
	if err != nil {
		t.Fatal(err)
	}
	defer pool.Close()
	rollback := errors.New("rollback fixture")
	err = db.Transaction(func(tx *gorm.DB) error {
		schema := "events_test_" + strings.ReplaceAll(uuid.NewString(), "-", "")
		for _, sql := range []string{"CREATE SCHEMA " + schema, "SET LOCAL search_path TO " + schema,
			`CREATE TABLE cities(id uuid PRIMARY KEY);
CREATE TABLE universities(id uuid PRIMARY KEY, city_id uuid REFERENCES cities(id));
CREATE TABLE users(id uuid PRIMARY KEY, university_id uuid REFERENCES universities(id), is_admin boolean DEFAULT false, nickname text, avatar_config jsonb);
CREATE TABLE campus_events (
    id uuid PRIMARY KEY,
    city_id uuid NOT NULL REFERENCES cities(id),
    organizer_id uuid REFERENCES users(id) ON DELETE SET NULL,
    title text NOT NULL CHECK (char_length(btrim(title)) BETWEEN 1 AND 120),
    location text NOT NULL CHECK (char_length(btrim(location)) BETWEEN 1 AND 200),
    description text NOT NULL DEFAULT '' CHECK (char_length(description) <= 10000),
    starts_at timestamptz NOT NULL,
    ends_at timestamptz,
    address text NOT NULL DEFAULT '',
    entry_info text NOT NULL DEFAULT '',
    poster jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO cities VALUES ('00000000-0000-0000-0000-000000000001'),('00000000-0000-0000-0000-000000000002');
INSERT INTO universities SELECT id,id FROM cities;
INSERT INTO users(id,university_id,is_admin) VALUES ('00000000-0000-0000-0000-000000000011','00000000-0000-0000-0000-000000000001',true),
('00000000-0000-0000-0000-000000000012','00000000-0000-0000-0000-000000000001',false),
('00000000-0000-0000-0000-000000000013','00000000-0000-0000-0000-000000000002',true);`} {
			if err := tx.Exec(sql).Error; err != nil {
				return err
			}
		}
		s := NewService(NewRepository(tx))
		admin, student, other := "00000000-0000-0000-0000-000000000011", "00000000-0000-0000-0000-000000000012", "00000000-0000-0000-0000-000000000013"
		input := Input{Title: "Evento", Location: "Campus", StartsAt: time.Now().Add(24 * time.Hour).Truncate(time.Second)}
		if _, err := s.Create(student, input); !errors.Is(err, ErrForbidden) {
			t.Fatalf("student wrote: %v", err)
		}
		for i := 0; i < 23; i++ {
			if _, err := s.Create(admin, input); err != nil {
				return err
			}
		}
		foreign, err := s.Create(other, input)
		if err != nil {
			return err
		}
		if err := tx.Exec("UPDATE users SET nickname='admin',avatar_config='{}'::jsonb WHERE id=?", admin).Error; err != nil {
			return err
		}
		got, err := s.Get(student, foreign.ID)
		if !errors.Is(err, ErrNotFound) || got != nil {
			t.Fatalf("foreign detail exposed: %+v %v", got, err)
		}
		owned, err := s.Create(admin, input)
		if err != nil {
			return err
		}
		got, err = s.Get(student, owned.ID)
		if err != nil {
			return err
		}
		if got.OrganizerID == nil || *got.OrganizerID != admin || got.OrganizerName != "admin" {
			t.Fatalf("publisher missing: %+v", got)
		}
		if _, err := s.Update(student, owned.ID, input); !errors.Is(err, ErrForbidden) {
			t.Fatalf("student updated: %v", err)
		}
		// Another city admin can edit without taking authorship or re-uploading the cover.
		editor := "00000000-0000-0000-0000-000000000014"
		if err := tx.Exec("INSERT INTO users(id,university_id,is_admin) SELECT ?, university_id,true FROM users WHERE id=?", editor, admin).Error; err != nil {
			return err
		}
		poster := &models.ImageMeta{URL: "https://example.test/existing.jpg", Width: 800, Height: 1200, Type: "photo", MIMEType: "image/jpeg"}
		if err := tx.Model(&Event{}).Where("id=?", owned.ID).Update("poster", poster).Error; err != nil {
			return err
		}
		changed := input
		changed.Poster = poster
		changed.Title = "Aggiornato"
		changed.EntryInfo = "Ingresso libero"
		end := input.StartsAt.Add(time.Hour)
		changed.EndsAt = &end
		updated, err := s.Update(editor, owned.ID, changed)
		if err != nil {
			return err
		}
		if updated.Title != "Aggiornato" || updated.Poster == nil || updated.Poster.URL != poster.URL || updated.EndsAt == nil || updated.OrganizerID == nil || *updated.OrganizerID != admin {
			t.Fatalf("bad update: %+v", updated)
		}
		if _, err := s.Update(other, owned.ID, changed); !errors.Is(err, ErrNotFound) {
			t.Fatalf("foreign update: %v", err)
		}
		if err := s.Delete(admin, owned.ID); err != nil {
			return err
		}
		// Yesterday is excluded; today's already-started events remain for the day.
		rome, _ := time.LoadLocation("Europe/Rome")
		now := time.Now().In(rome)
		today := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, rome)
		for _, date := range []time.Time{today.Add(-time.Hour), today.Add(time.Second)} {
			e := Event{ID: uuid.NewString(), CityID: "00000000-0000-0000-0000-000000000001", Title: "Fixture", Location: "Campus", StartsAt: date}
			if err := tx.Create(&e).Error; err != nil {
				return err
			}
		}
		page, err := s.List(student, "")
		if err != nil {
			return err
		}
		if len(page.Events) != 20 || page.NextCursor == "" || page.CanManage {
			t.Fatalf("bad first page: %+v", page)
		}
		next, err := s.List(student, page.NextCursor)
		if err != nil {
			return err
		}
		if len(next.Events) != 4 || next.NextCursor != "" {
			t.Fatalf("bad next page: %+v", next)
		}
		ids := map[string]bool{}
		for _, e := range append(page.Events, next.Events...) {
			if ids[e.ID] || e.ID == foreign.ID || e.StartsAt.Before(today) {
				t.Fatalf("duplicate, foreign or past event: %+v", e)
			}
			ids[e.ID] = true
		}
		if err := s.Delete(admin, foreign.ID); !errors.Is(err, ErrNotFound) {
			t.Fatalf("foreign delete: %v", err)
		}
		if err := s.Delete(student, page.Events[0].ID); !errors.Is(err, ErrForbidden) {
			t.Fatalf("student delete: %v", err)
		}
		if err := s.Delete(admin, page.Events[0].ID); err != nil {
			return err
		}
		if err := s.Delete(admin, page.Events[0].ID); !errors.Is(err, ErrNotFound) {
			t.Fatalf("repeat delete: %v", err)
		}
		if err := tx.Exec("UPDATE users SET is_admin=false WHERE id=?", admin).Error; err != nil {
			return err
		}
		if _, err := s.Create(admin, input); !errors.Is(err, ErrForbidden) {
			t.Fatalf("revoked admin wrote: %v", err)
		}
		return rollback
	})
	if !errors.Is(err, rollback) {
		t.Fatal(err)
	}
}
