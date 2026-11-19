package users

import (
	"encoding/json"
	"errors"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	"axoo-server/internal/models"
	"axoo-server/internal/moderation"
	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
)

func TestPublicProfilesDatabase(t *testing.T) {
	dsn := os.Getenv("AXOO_USERS_TEST_DB_URL")
	if dsn == "" {
		t.Skip("set AXOO_USERS_TEST_DB_URL for an isolated PostgreSQL test")
	}
	db, err := gorm.Open(postgres.New(postgres.Config{DSN: dsn, PreferSimpleProtocol: true}), &gorm.Config{})
	if err != nil {
		t.Fatal(err)
	}
	sqlDB, err := db.DB()
	if err != nil {
		t.Fatal(err)
	}
	defer sqlDB.Close()
	rollback := errors.New("rollback fixture")
	err = db.Transaction(func(tx *gorm.DB) error {
		schema := "profiles_test_" + strings.ReplaceAll(uuid.NewString(), "-", "")
		if err := tx.Exec("CREATE SCHEMA " + schema).Error; err != nil {
			return err
		}
		if err := tx.Exec("SET LOCAL search_path TO " + schema).Error; err != nil {
			return err
		}
		fixture := `
CREATE TABLE universities (id uuid PRIMARY KEY, name text, city_id uuid);
CREATE TABLE users (
    id uuid PRIMARY KEY, nickname text, university_id uuid, avatar_config jsonb,
    is_banned boolean DEFAULT false, suspended_until timestamptz, created_at timestamptz DEFAULT now(),
    bio text NOT NULL DEFAULT '' CHECK (char_length(bio) <= 200),
    bio_entities jsonb NOT NULL DEFAULT '[]', is_profile_public boolean NOT NULL DEFAULT false
);
CREATE TABLE user_blocks (blocker_id uuid, blocked_id uuid, PRIMARY KEY(blocker_id, blocked_id));
CREATE TABLE reports (id uuid PRIMARY KEY, reporter_id uuid, target_type text CHECK (target_type IN ('post', 'comment', 'user')), target_id uuid, reason text, moderation_action_id uuid, created_at timestamptz DEFAULT now());
CREATE TABLE moderation_actions (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), moderator_id uuid, action text, target_type text, target_id uuid, reason text, duration_hours int, author_seen_at timestamptz, created_at timestamptz DEFAULT now());
INSERT INTO universities VALUES
('00000000-0000-0000-0000-000000000011','Campus A','00000000-0000-0000-0000-000000000021'),
('00000000-0000-0000-0000-000000000012','Campus B','00000000-0000-0000-0000-000000000021'),
('00000000-0000-0000-0000-000000000013','Other city','00000000-0000-0000-0000-000000000022');
INSERT INTO users (id,nickname,university_id,avatar_config) VALUES
('00000000-0000-0000-0000-000000000001','viewer','00000000-0000-0000-0000-000000000011',NULL),
('00000000-0000-0000-0000-000000000002','student','00000000-0000-0000-0000-000000000012','{"seed":"cat"}'),
('00000000-0000-0000-0000-000000000003','othercity','00000000-0000-0000-0000-000000000013',NULL);
CREATE TABLE posts (id uuid, city_id uuid);
CREATE TABLE comments (id uuid, post_id uuid);
`
		if err := tx.Exec(fixture).Error; err != nil {
			return err
		}
		viewer := "00000000-0000-0000-0000-000000000001"
		target := "00000000-0000-0000-0000-000000000002"
		repo := NewRepository(tx)
		service := NewService(repo)
		app := fiber.New()
		app.Use(func(c *fiber.Ctx) error { c.Locals("user_id", viewer); return c.Next() })
		NewHandler(service).RegisterRoutes(app)
		check := func(method, path, body string, status int) map[string]any {
			t.Helper()
			request := httptest.NewRequest(method, path, strings.NewReader(body))
			request.Header.Set("Content-Type", "application/json")
			response, err := app.Test(request)
			if err != nil {
				t.Fatal(err)
			}
			defer response.Body.Close()
			var result map[string]any
			if err := json.NewDecoder(response.Body).Decode(&result); err != nil {
				t.Fatal(err)
			}
			if response.StatusCode != status {
				t.Fatalf("%s %s: status=%d body=%v", method, path, response.StatusCode, result)
			}
			return result
		}
		profile := check("GET", "/users/"+target, "", 200)
		if len(profile) != 8 || profile["bio"] != "" || profile["university_name"] != "Campus B" || profile["is_profile_public"] != false {
			t.Fatalf("profile: %v", profile)
		}
		check("GET", "/users/not-a-uuid", "", 404)
		check("GET", "/users/00000000-0000-0000-0000-000000000099", "", 404)
		check("GET", "/users/00000000-0000-0000-0000-000000000003", "", 404)
		check("PUT", "/users/me/bio", `{}`, 400)
		check("PUT", "/users/me/bio", `{"bio":null}`, 400)
		check("PUT", "/users/me/bio", `{"bio":"`+strings.Repeat("😀", 201)+`"}`, 400)
		updated := check("PUT", "/users/me/bio", `{"bio":"  Ciao 🐱\n  "}`, 200)
		if updated["id"] != viewer || updated["bio"] != "Ciao 🐱" {
			t.Fatalf("update: %v", updated)
		}
		emojiBody := `{"bio":"  😀 🐱  ","bio_entities":[{"type":"custom_emoji","offset":4,"length":1,"custom_emoji_id":42}]}`
		updated = check("PUT", "/users/me/bio", emojiBody, 200)
		entities := updated["bio_entities"].([]any)
		if updated["bio"] != "😀 🐱" || len(entities) != 1 || entities[0].(map[string]any)["offset"] != float64(2) {
			t.Fatalf("emoji not preserved: %v", updated)
		}
		profile = check("GET", "/users/"+viewer, "", 200)
		entities = profile["bio_entities"].([]any)
		if len(entities) != 1 || entities[0].(map[string]any)["custom_emoji_id"] != float64(42) {
			t.Fatalf("public bio emoji lost: %v", profile)
		}
		check("PUT", "/users/me/bio", `{"bio":"x","bio_entities":[{"type":"custom_emoji","offset":2,"length":1,"custom_emoji_id":42}]}`, 400)
		check("PUT", "/users/me/bio", `{"bio":"`+strings.Repeat("😀", 200)+`"}`, 200)
		check("PUT", "/users/me/bio", `{"bio":""}`, 200)
		profile = check("GET", "/users/me", "", 200)
		if profile["bio"] != "" {
			t.Fatalf("clear failed: %v", profile)
		}
		if entities := profile["bio_entities"].([]any); len(entities) != 0 {
			t.Fatalf("clear left emoji entities: %v", entities)
		}
		if err := repo.BlockUser(target, viewer); err != nil {
			return err
		}
		check("GET", "/users/"+target, "", 200) // Blocking remains a personal, one-way preference.
		if err := repo.BlockUser(viewer, target); err != nil {
			return err
		}
		check("GET", "/users/"+target, "", 404)
		if err := repo.UnblockUser(viewer, target); err != nil {
			return err
		}
		check("GET", "/users/"+target, "", 200)
		if err := tx.Exec("UPDATE users SET suspended_until = NOW() + INTERVAL '1 hour' WHERE id = ?", target).Error; err != nil {
			return err
		}
		check("GET", "/users/"+target, "", 404)
		if err := tx.Exec("UPDATE users SET suspended_until = NOW() - INTERVAL '1 hour' WHERE id = ?", target).Error; err != nil {
			return err
		}
		check("GET", "/users/"+target, "", 200)
		if err := tx.Exec("UPDATE users SET is_banned = true WHERE id = ?", target).Error; err != nil {
			return err
		}
		check("GET", "/users/"+target, "", 404)
		moderationRepo := moderation.NewRepository(tx)
		if err := tx.Create(&models.Report{ID: uuid.NewString(), ReporterID: viewer, TargetType: "user", TargetID: target, Reason: "harassment"}).Error; err != nil {
			return err
		}
		queue, err := moderationRepo.ListOpenReports([]string{"00000000-0000-0000-0000-000000000021"}, 100)
		if err != nil || len(queue) != 1 {
			t.Fatalf("profile report missing from queue: %v %v", queue, err)
		}
		userTarget, err := moderationRepo.FindTarget("user", target)
		if err != nil || userTarget.AuthorID != target {
			t.Fatalf("profile moderation target: %v %v", userTarget, err)
		}
		if err := moderationRepo.Apply(viewer, moderation.ActionInput{Action: moderation.ActionKeep, TargetType: "user", TargetID: target}); err != nil {
			return err
		}
		queue, err = moderationRepo.ListOpenReports([]string{"00000000-0000-0000-0000-000000000021"}, 100)
		if err != nil || len(queue) != 0 {
			t.Fatalf("profile report was not resolved: %v %v", queue, err)
		}
		return rollback
	})
	if !errors.Is(err, rollback) {
		t.Fatal(err)
	}
}
