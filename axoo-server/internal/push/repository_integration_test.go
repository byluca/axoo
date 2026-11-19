package push

import (
	"context"
	"os"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/stdlib"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

const (
	ownerID       = "11111111-1111-4111-8111-111111111111"
	actorID       = "22222222-2222-4222-8222-222222222222"
	parentOwnerID = "33333333-3333-4333-8333-333333333333"
	postID        = "44444444-4444-4444-8444-444444444444"
	parentID      = "55555555-5555-4555-8555-555555555555"
	commentID     = "66666666-6666-4666-8666-666666666666"
	replyID       = "77777777-7777-4777-8777-777777777777"
)

func pushTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	dsn := os.Getenv("AXOO_PUSH_TEST_DSN")
	if dsn == "" {
		t.Skip("set AXOO_PUSH_TEST_DSN to a disposable local PostgreSQL database")
	}
	config, err := pgx.ParseConfig(dsn)
	if err != nil {
		t.Fatal("invalid test database configuration")
	}
	if config.Host != "localhost" && config.Host != "127.0.0.1" && config.Host != "::1" && !strings.HasPrefix(config.Host, "/") {
		t.Fatal("push database tests require a local PostgreSQL instance")
	}
	config.ConnectTimeout = 5 * time.Second
	adminSQL := stdlib.OpenDB(*config)
	admin, err := gorm.Open(postgres.New(postgres.Config{Conn: adminSQL}), &gorm.Config{Logger: logger.Default.LogMode(logger.Silent)})
	if err != nil {
		adminSQL.Close()
		t.Fatal("cannot connect to test database")
	}
	schema := "push_test_" + strings.ReplaceAll(uuid.NewString(), "-", "")
	if err := admin.Exec("CREATE SCHEMA " + schema).Error; err != nil {
		adminSQL.Close()
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := admin.Exec("DROP SCHEMA " + schema + " CASCADE").Error; err != nil {
			t.Error(err)
		}
		adminSQL.Close()
	})
	config.RuntimeParams["search_path"] = schema
	sqlDB := stdlib.OpenDB(*config)
	t.Cleanup(func() { sqlDB.Close() })
	db, err := gorm.Open(postgres.New(postgres.Config{Conn: sqlDB}), &gorm.Config{Logger: logger.Default.LogMode(logger.Silent)})
	if err != nil {
		t.Fatal(err)
	}
	sql := `
		CREATE TABLE users (id uuid PRIMARY KEY, is_banned boolean NOT NULL DEFAULT false, suspended_until timestamptz);
		CREATE TABLE posts (id uuid PRIMARY KEY, user_id uuid REFERENCES users(id), is_hidden boolean NOT NULL DEFAULT false, is_deleted boolean NOT NULL DEFAULT false);
		CREATE TABLE comments (id uuid PRIMARY KEY, post_id uuid REFERENCES posts(id), user_id uuid REFERENCES users(id), parent_comment_id uuid REFERENCES comments(id), is_hidden boolean NOT NULL DEFAULT false, is_deleted boolean NOT NULL DEFAULT false);
		CREATE TABLE user_blocks (blocker_id uuid, blocked_id uuid);
		CREATE TABLE user_hidden_posts (user_id uuid, post_id uuid);
		CREATE TABLE push_installations (
			installation_id uuid PRIMARY KEY,
			user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
			registration text NOT NULL UNIQUE CHECK (length(registration) BETWEEN 1 AND 4096),
			updated_at timestamptz NOT NULL DEFAULT now()
		);
	`
	if err := db.Exec(sql).Error; err != nil {
		t.Fatal(err)
	}
	for _, id := range []string{ownerID, actorID, parentOwnerID} {
		if err := db.Exec("INSERT INTO users (id) VALUES (?)", id).Error; err != nil {
			t.Fatal(err)
		}
	}
	return db
}

func TestInstallationDatabaseLifecycle(t *testing.T) {
	db := pushTestDB(t)
	repo := NewRepository(db)
	ctx := context.Background()
	register := func(user, id, registration string) {
		t.Helper()
		if err := repo.Register(ctx, user, id, registration); err != nil {
			t.Fatal(err)
		}
	}
	assertRows := func(want int) []Installation {
		t.Helper()
		var rows []Installation
		if err := db.Order("installation_id").Find(&rows).Error; err != nil {
			t.Fatal(err)
		}
		if len(rows) != want {
			t.Fatalf("installations = %d, want %d", len(rows), want)
		}
		return rows
	}
	register(ownerID, installationID, "first")
	firstUpdate := assertRows(1)[0].UpdatedAt
	register(ownerID, installationID, "first")
	if !assertRows(1)[0].UpdatedAt.After(firstUpdate) {
		t.Fatal("repeated registration did not refresh its timestamp")
	}
	register(ownerID, installationID, "rotated")
	if err := repo.Remove(ctx, ownerID, installationID, "first"); err != nil {
		t.Fatal(err)
	}
	if assertRows(1)[0].Registration != "rotated" {
		t.Fatal("old logout removed a rotated registration")
	}
	secondID := uuid.NewString()
	register(ownerID, secondID, "second-phone")
	assertRows(2)
	register(actorID, installationID, "rotated")
	if err := repo.Remove(ctx, ownerID, installationID, "rotated"); err != nil {
		t.Fatal(err)
	}
	assertRows(2)
	// Reusing a registration under a new installation transfers it atomically.
	newID := uuid.NewString()
	register(actorID, newID, "rotated")
	rows := assertRows(2)
	for _, row := range rows {
		if row.InstallationID == installationID {
			t.Fatal("registration is still bound to the old installation")
		}
	}
	// A failed binding must roll back any removal of the old registration.
	if err := repo.Register(ctx, uuid.NewString(), uuid.NewString(), "rotated"); err == nil {
		t.Fatal("registration accepted a nonexistent account")
	}
	assertRows(2)
	if err := db.Exec("DELETE FROM users WHERE id = ?", actorID).Error; err != nil {
		t.Fatal(err)
	}
	if assertRows(1)[0].UserID != ownerID {
		t.Fatal("account deletion did not cascade to its installations")
	}
	if err := repo.Remove(ctx, ownerID, secondID, "second-phone"); err != nil {
		t.Fatal(err)
	}
	if err := repo.Remove(ctx, ownerID, secondID, "second-phone"); err != nil {
		t.Fatal("repeated logout is not idempotent")
	}
	assertRows(0)
}

func TestConcurrentRegistrationIsBoundOnlyOnce(t *testing.T) {
	db := pushTestDB(t)
	repo := NewRepository(db)
	errors := make(chan error, 8)
	var group sync.WaitGroup
	for index := range 8 {
		userID := ownerID
		if index%2 == 1 {
			userID = actorID
		}
		group.Add(1)
		go func() {
			defer group.Done()
			errors <- repo.Register(context.Background(), userID, uuid.NewString(), "shared-registration")
		}()
	}
	group.Wait()
	close(errors)
	for err := range errors {
		if err != nil {
			t.Fatal(err)
		}
	}
	var count int64
	if err := db.Model(&Installation{}).Count(&count).Error; err != nil || count != 1 {
		t.Fatalf("concurrent binding count = %d, error = %v", count, err)
	}
}

func TestCommentRecipientsDatabase(t *testing.T) {
	db := pushTestDB(t)
	for _, statement := range []struct {
		sql  string
		args []any
	}{
		{"INSERT INTO posts (id, user_id) VALUES (?, ?)", []any{postID, ownerID}},
		{"INSERT INTO comments (id, post_id, user_id) VALUES (?, ?, ?)", []any{parentID, postID, parentOwnerID}},
		{"INSERT INTO comments (id, post_id, user_id) VALUES (?, ?, ?)", []any{commentID, postID, actorID}},
		{"INSERT INTO comments (id, post_id, user_id, parent_comment_id) VALUES (?, ?, ?, ?)", []any{replyID, postID, actorID, parentID}},
	} {
		if err := db.Exec(statement.sql, statement.args...).Error; err != nil {
			t.Fatal(err)
		}
	}
	for _, installation := range []struct{ user, registration string }{
		{ownerID, "owner-android"}, {ownerID, "owner-ios"}, {parentOwnerID, "parent-phone"}, {actorID, "actor-phone"},
	} {
		if err := NewRepository(db).Register(context.Background(), installation.user, uuid.NewString(), installation.registration); err != nil {
			t.Fatal(err)
		}
	}
	for _, tc := range []struct {
		name, comment, mutation, recipient string
		args                               []any
		want                               int
	}{
		{name: "top-level comment", comment: commentID, recipient: ownerID, want: 2},
		{name: "reply only to parent owner", comment: replyID, recipient: parentOwnerID, want: 1},
		{name: "self comment", comment: commentID, mutation: "UPDATE comments SET user_id = ? WHERE id = ?", args: []any{ownerID, commentID}},
		{name: "self reply", comment: replyID, mutation: "UPDATE comments SET user_id = ? WHERE id = ?", args: []any{parentOwnerID, replyID}},
		{name: "blocked commenter", comment: commentID, mutation: "INSERT INTO user_blocks VALUES (?, ?)", args: []any{ownerID, actorID}},
		{name: "blocked post author", comment: replyID, mutation: "INSERT INTO user_blocks VALUES (?, ?)", args: []any{parentOwnerID, ownerID}},
		{name: "one-way block", comment: commentID, mutation: "INSERT INTO user_blocks VALUES (?, ?)", args: []any{actorID, ownerID}, recipient: ownerID, want: 2},
		{name: "personally hidden post", comment: commentID, mutation: "INSERT INTO user_hidden_posts VALUES (?, ?)", args: []any{ownerID, postID}},
		{name: "hidden post", comment: commentID, mutation: "UPDATE posts SET is_hidden = true"},
		{name: "deleted post", comment: commentID, mutation: "UPDATE posts SET is_deleted = true"},
		{name: "hidden comment", comment: commentID, mutation: "UPDATE comments SET is_hidden = true WHERE id = ?", args: []any{commentID}},
		{name: "deleted comment", comment: commentID, mutation: "UPDATE comments SET is_deleted = true WHERE id = ?", args: []any{commentID}},
		{name: "hidden parent", comment: replyID, mutation: "UPDATE comments SET is_hidden = true WHERE id = ?", args: []any{parentID}},
		{name: "deleted parent", comment: replyID, mutation: "UPDATE comments SET is_deleted = true WHERE id = ?", args: []any{parentID}},
		{name: "banned recipient", comment: commentID, mutation: "UPDATE users SET is_banned = true WHERE id = ?", args: []any{ownerID}},
		{name: "suspended recipient", comment: commentID, mutation: "UPDATE users SET suspended_until = NOW() + interval '1 day' WHERE id = ?", args: []any{ownerID}},
		{name: "expired suspension", comment: commentID, mutation: "UPDATE users SET suspended_until = NOW() - interval '1 day' WHERE id = ?", args: []any{ownerID}, recipient: ownerID, want: 2},
	} {
		t.Run(tc.name, func(t *testing.T) {
			tx := db.Begin()
			defer tx.Rollback()
			if tx.Error != nil {
				t.Fatal(tx.Error)
			}
			if tc.mutation != "" {
				if err := tx.Exec(tc.mutation, tc.args...).Error; err != nil {
					t.Fatal(err)
				}
			}
			installations, err := NewRepository(tx).ForComment(context.Background(), tc.comment)
			if err != nil || len(installations) != tc.want {
				t.Fatalf("installations = %d, want %d; error = %v", len(installations), tc.want, err)
			}
			for _, installation := range installations {
				if installation.UserID != tc.recipient {
					t.Fatalf("recipient = %s, want %s", installation.UserID, tc.recipient)
				}
			}
		})
	}
}
