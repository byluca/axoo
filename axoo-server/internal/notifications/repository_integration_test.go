package notifications

import (
	"errors"
	"os"
	"strings"
	"testing"

	"github.com/google/uuid"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
)

// Isolated schema inside a rolled-back transaction. Never uses application credentials.
func TestNotificationsDatabase(t *testing.T) {
	dsn := os.Getenv("AXOO_NOTIFICATIONS_TEST_DB_URL")
	if dsn == "" {
		t.Skip("set AXOO_NOTIFICATIONS_TEST_DB_URL for an isolated PostgreSQL test")
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
	notificationsFixture, err := os.ReadFile("testdata/notifications.sql")
	if err != nil {
		t.Fatal(err)
	}
	rollback := errors.New("rollback fixture")
	err = db.Transaction(func(tx *gorm.DB) error {
		schema := "notifications_test_" + strings.ReplaceAll(uuid.NewString(), "-", "")
		if err := tx.Exec("CREATE SCHEMA " + schema).Error; err != nil {
			return err
		}
		if err := tx.Exec("SET LOCAL search_path TO " + schema).Error; err != nil {
			return err
		}
		fixture := `
CREATE TABLE users (id uuid PRIMARY KEY, nickname text, is_banned boolean DEFAULT false, suspended_until timestamptz);
CREATE TABLE posts (id uuid PRIMARY KEY, user_id uuid REFERENCES users(id), body text DEFAULT 'Post', entities jsonb NOT NULL DEFAULT '[]', is_deleted boolean DEFAULT false, is_hidden boolean DEFAULT false);
CREATE TABLE comments (id uuid PRIMARY KEY, post_id uuid REFERENCES posts(id), user_id uuid REFERENCES users(id), parent_comment_id uuid REFERENCES comments(id), body text DEFAULT 'Testo', entities jsonb NOT NULL DEFAULT '[]', is_deleted boolean DEFAULT false, is_hidden boolean DEFAULT false);
CREATE TABLE user_blocks (blocker_id uuid, blocked_id uuid);
CREATE TABLE user_hidden_posts (user_id uuid, post_id uuid);
INSERT INTO users (id,nickname) VALUES ('00000000-0000-0000-0000-000000000001','Marco'),('00000000-0000-0000-0000-000000000002','Giulia'),('00000000-0000-0000-0000-000000000003','Luca');
INSERT INTO posts (id,user_id) VALUES ('00000000-0000-0000-0000-000000000010','00000000-0000-0000-0000-000000000001');
`
		if err := tx.Exec(fixture).Error; err != nil {
			return err
		}
		if err := tx.Exec(string(notificationsFixture)).Error; err != nil {
			return err
		}
		owner := "00000000-0000-0000-0000-000000000001"
		actor := "00000000-0000-0000-0000-000000000002"
		other := "00000000-0000-0000-0000-000000000003"
		post := "00000000-0000-0000-0000-000000000010"
		repo := NewRepository(tx)
		add := func(user string, parent *string) string {
			id := uuid.NewString()
			if err := tx.Exec("INSERT INTO comments (id,post_id,user_id,parent_comment_id) VALUES (?,?,?,?)", id, post, user, parent).Error; err != nil {
				t.Fatal(err)
			}
			if err := RecordComment(tx, id); err != nil {
				t.Fatal(err)
			}
			return id
		}
		first := add(actor, nil)
		preview := "😀\n" + strings.Repeat("a", 156) + "❤️"
		if err := tx.Exec(`UPDATE comments SET body=?, entities=?::jsonb WHERE id=?`, preview+"x",
			`[{"type":"custom_emoji","offset":158,"length":2,"custom_emoji_id":42},{"type":"custom_emoji","offset":160,"length":1,"custom_emoji_id":43}]`, first).Error; err != nil {
			return err
		}
		if err := RecordComment(tx, first); err != nil {
			return err
		}
		count, err := repo.Unread(owner)
		if err != nil {
			return err
		}
		if count != 1 {
			t.Fatalf("idempotency count=%d", count)
		}
		add(other, &first)
		count, err = repo.Unread(actor)
		if err != nil {
			return err
		}
		if count != 1 {
			t.Fatal("reply did not notify comment owner")
		}
		add(owner, nil)
		count, err = repo.Unread(owner)
		if err != nil {
			return err
		}
		if count != 1 {
			t.Fatal("self notification")
		}
		add(other, nil)
		page, err := repo.List(owner, "", 1)
		if err != nil {
			return err
		}
		if len(page.Notifications) != 1 || page.NextCursor == "" || page.Notifications[0].Message != "Luca ha commentato il tuo post" {
			t.Fatalf("incorrect first page %+v", page)
		}
		next, err := repo.List(owner, page.NextCursor, 1)
		if err != nil {
			return err
		}
		if len(next.Notifications) != 1 || next.Notifications[0].CommentID != first || next.NextCursor != "" {
			t.Fatal("incorrect pagination")
		}
		notification := next.Notifications[0]
		if notification.Preview != preview || len(notification.PreviewEntities) != 1 ||
			notification.PreviewEntities[0].Offset != 158 || notification.PreviewEntities[0].CustomEmojiID != 42 {
			t.Fatalf("preview lost or truncated emoji metadata: %+v", notification)
		}
		newest := add(actor, nil)
		count, err = repo.Seen(owner, page.SeenThrough)
		if err != nil {
			return err
		}
		if count != 1 {
			t.Fatal("acknowledgement swallowed a new arrival")
		}
		count, err = repo.Unread(actor)
		if err != nil {
			return err
		}
		if count != 1 {
			t.Fatal("acknowledgement affected another recipient")
		}
		if err := tx.Exec("INSERT INTO user_blocks VALUES (?,?)", owner, actor).Error; err != nil {
			return err
		}
		add(actor, nil)
		count, err = repo.Unread(owner)
		if err != nil {
			return err
		}
		if count != 0 {
			t.Fatal("blocked author remains visible")
		}
		if err := tx.Exec("DELETE FROM user_blocks").Error; err != nil {
			return err
		}
		if err := tx.Exec("UPDATE comments SET is_hidden=true WHERE id=?", newest).Error; err != nil {
			return err
		}
		count, err = repo.Unread(owner)
		if err != nil {
			return err
		}
		if count != 0 {
			t.Fatal("hidden comment remains counted")
		}
		if err := tx.Exec("INSERT INTO user_hidden_posts VALUES (?,?)", owner, post).Error; err != nil {
			return err
		}
		hidden, err := repo.List(owner, "", 20)
		if err != nil {
			return err
		}
		if len(hidden.Notifications) != 0 {
			t.Fatal("hidden post remains visible")
		}
		if err := tx.Exec("DELETE FROM user_hidden_posts").Error; err != nil {
			return err
		}
		pendingID := uuid.NewString()
		err = tx.Transaction(func(inner *gorm.DB) error {
			if err := inner.Exec("INSERT INTO comments (id,post_id,user_id) VALUES (?,?,?)", pendingID, post, actor).Error; err != nil {
				return err
			}
			if err := RecordComment(inner, pendingID); err != nil {
				return err
			}
			return rollback
		})
		if !errors.Is(err, rollback) {
			return err
		}
		var rows int64
		if err := tx.Table("notifications").Where("comment_id=?", pendingID).Count(&rows).Error; err != nil {
			return err
		}
		if rows != 0 {
			t.Fatal("rolled-back comment left a notification")
		}
		if err := tx.Exec("DELETE FROM comments WHERE parent_comment_id=?", first).Error; err != nil {
			return err
		}
		if err := tx.Exec("DELETE FROM comments WHERE id=?", first).Error; err != nil {
			return err
		}
		if err := tx.Table("notifications").Where("comment_id=?", first).Count(&rows).Error; err != nil {
			return err
		}
		if rows != 0 {
			t.Fatal("notification did not cascade with deleted comment")
		}
		return rollback
	})
	if !errors.Is(err, rollback) {
		t.Fatal(err)
	}
}
