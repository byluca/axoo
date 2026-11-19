package notifications_test

import (
	"fmt"
	"net/url"
	"os"
	"strings"
	"sync"
	"testing"

	"axoo-server/internal/comments"
	"axoo-server/internal/notifications"
	"axoo-server/internal/posts"
	"github.com/google/uuid"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func TestAppreciationVotesDatabase(t *testing.T) {
	dsn := os.Getenv("AXOO_NOTIFICATIONS_TEST_DB_URL")
	if dsn == "" {
		t.Skip("set AXOO_NOTIFICATIONS_TEST_DB_URL for isolated PostgreSQL tests")
	}
	config := &gorm.Config{Logger: logger.Default.LogMode(logger.Silent)}
	admin, err := gorm.Open(postgres.Open(dsn), config)
	if err != nil {
		t.Fatal(err)
	}
	adminSQL, _ := admin.DB()
	defer adminSQL.Close()
	schema := "appreciation_" + strings.ReplaceAll(uuid.NewString(), "-", "")
	must := func(err error) {
		t.Helper()
		if err != nil {
			t.Fatal(err)
		}
	}
	must(admin.Exec("CREATE SCHEMA " + schema).Error)
	defer admin.Exec("DROP SCHEMA " + schema + " CASCADE")
	parsed, err := url.Parse(dsn)
	must(err)
	query := parsed.Query()
	query.Set("search_path", schema)
	parsed.RawQuery = query.Encode()
	db, err := gorm.Open(postgres.New(postgres.Config{DSN: parsed.String(), PreferSimpleProtocol: true}), config)
	must(err)
	sqlDB, _ := db.DB()
	defer sqlDB.Close()
	must(db.Exec(`
CREATE TABLE users (id uuid PRIMARY KEY,nickname text,is_banned boolean DEFAULT false,suspended_until timestamptz);
CREATE TABLE posts (id uuid PRIMARY KEY,user_id uuid REFERENCES users(id),body text DEFAULT 'Post apprezzato',entities jsonb NOT NULL DEFAULT '[]',is_deleted boolean DEFAULT false,is_hidden boolean DEFAULT false);
CREATE TABLE comments (id uuid PRIMARY KEY,post_id uuid REFERENCES posts(id),user_id uuid REFERENCES users(id),parent_comment_id uuid REFERENCES comments(id),body text DEFAULT 'Commento apprezzato',entities jsonb NOT NULL DEFAULT '[]',is_deleted boolean DEFAULT false,is_hidden boolean DEFAULT false);
CREATE TABLE post_votes (post_id uuid REFERENCES posts(id),user_id uuid REFERENCES users(id),type text,created_at timestamptz,PRIMARY KEY(post_id,user_id));
CREATE TABLE comment_votes (comment_id uuid REFERENCES comments(id),user_id uuid REFERENCES users(id),type text,created_at timestamptz,PRIMARY KEY(comment_id,user_id));
CREATE TABLE user_blocks (blocker_id uuid,blocked_id uuid);
CREATE TABLE user_hidden_posts (user_id uuid,post_id uuid);
`).Error)
	notificationsFixture, err := os.ReadFile("testdata/notifications.sql")
	must(err)
	must(db.Exec(string(notificationsFixture)).Error)
	owner, commentOwner, post, comment := uuid.NewString(), uuid.NewString(), uuid.NewString(), uuid.NewString()
	must(db.Exec("INSERT INTO users(id,nickname) VALUES (?, 'Autore'), (?, 'Commentatore')", owner, commentOwner).Error)
	must(db.Exec("INSERT INTO posts(id,user_id) VALUES (?,?)", post, owner).Error)
	must(db.Exec("INSERT INTO comments(id,post_id,user_id) VALUES (?,?,?)", comment, post, commentOwner).Error)
	voters := make([]string, 30)
	for i := range voters {
		voters[i] = uuid.NewString()
		must(db.Exec("INSERT INTO users(id,nickname) VALUES (?,?)", voters[i], fmt.Sprint("Votante", i)).Error)
	}
	postRepo, commentRepo, inbox := posts.NewRepository(db), comments.NewRepository(db), notifications.NewRepository(db)
	count := func(user string, want int64) {
		t.Helper()
		n, err := inbox.Unread(user)
		must(err)
		if n != want {
			t.Fatalf("unread=%d want=%d", n, want)
		}
	}
	for i := 0; i < 9; i++ {
		_, err := postRepo.SetVote(post, voters[i], 1)
		must(err)
	}
	count(owner, 0)
	// Two simultaneous votes crossing 10 must not lose or duplicate the milestone.
	var wg sync.WaitGroup
	failures := make(chan error, 2)
	for i := 9; i < 11; i++ {
		wg.Add(1)
		go func(v string) { defer wg.Done(); _, err := postRepo.SetVote(post, v, 1); failures <- err }(voters[i])
	}
	wg.Wait()
	close(failures)
	for err := range failures {
		must(err)
	}
	count(owner, 1)
	_, err = postRepo.SetVote(post, voters[9], 1)
	must(err) // Retry is idempotent.
	_, err = postRepo.SetVote(post, voters[10], 0)
	must(err)
	_, err = postRepo.SetVote(post, voters[9], -1)
	must(err)
	_, err = postRepo.SetVote(post, voters[9], 1)
	must(err) // Re-cross 10.
	count(owner, 1)
	for i := 10; i < 25; i++ {
		_, err = postRepo.SetVote(post, voters[i], 1)
		must(err)
	}
	count(owner, 2)
	_, err = postRepo.SetVote(post, voters[25], 1)
	must(err)
	count(owner, 2) // No notification between milestones.
	for i := 0; i < 4; i++ {
		_, err = commentRepo.SetVote(comment, voters[i], 1)
		must(err)
	}
	count(commentOwner, 0)
	_, err = commentRepo.SetVote(comment, voters[4], 1)
	must(err)
	count(commentOwner, 1)
	_, err = commentRepo.SetVote(comment, voters[4], 0)
	must(err)
	_, err = commentRepo.SetVote(comment, voters[4], 1)
	must(err)
	count(commentOwner, 1)
	page, err := inbox.List(owner, "", 1)
	must(err)
	if len(page.Notifications) != 1 || page.Notifications[0].Message != "Il tuo post ha ricevuto 25 voti positivi" || page.Notifications[0].CommentID != "" || page.NextCursor == "" || page.Notifications[0].Preview != "Post apprezzato" {
		t.Fatalf("post page: %+v", page)
	}
	rest, err := inbox.List(owner, page.NextCursor, 20)
	must(err)
	if len(rest.Notifications) != 1 || rest.Notifications[0].Message != "Il tuo post ha ricevuto 10 voti positivi" {
		t.Fatalf("second page: %+v", rest)
	}
	_, err = inbox.Seen(owner, page.SeenThrough)
	must(err)
	count(owner, 0)
	count(commentOwner, 1)
	page, err = inbox.List(commentOwner, "", 20)
	must(err)
	if page.Notifications[0].CommentID != comment || page.Notifications[0].Message != "Il tuo commento ha ricevuto 5 voti positivi" || page.Notifications[0].Preview != "Commento apprezzato" {
		t.Fatalf("comment page: %+v", page)
	}
	must(db.Exec("UPDATE comments SET is_hidden=true WHERE id=?", comment).Error)
	count(commentOwner, 0)
	must(db.Exec("UPDATE comments SET is_hidden=false WHERE id=?", comment).Error)
	must(db.Exec("INSERT INTO user_blocks VALUES (?,?)", commentOwner, owner).Error)
	count(commentOwner, 0)
	must(db.Exec("DELETE FROM user_blocks").Error)
	must(db.Exec("INSERT INTO user_hidden_posts VALUES (?,?)", commentOwner, post).Error)
	count(commentOwner, 0)
	must(db.Exec("DELETE FROM user_hidden_posts").Error)
	must(db.Exec("UPDATE users SET is_banned=true WHERE id=?", commentOwner).Error)
	count(commentOwner, 0)
	must(db.Exec("UPDATE users SET is_banned=false WHERE id=?", commentOwner).Error)
	count(commentOwner, 1)
	// A failed transaction must roll back the vote and its milestone together.
	rollbackPost := uuid.NewString()
	must(db.Exec("INSERT INTO posts(id,user_id) VALUES (?,?)", rollbackPost, owner).Error)
	for i := 0; i < 9; i++ {
		_, err = postRepo.SetVote(rollbackPost, voters[i], 1)
		must(err)
	}
	err = db.Transaction(func(tx *gorm.DB) error {
		if _, err := posts.NewRepository(tx).SetVote(rollbackPost, voters[9], 1); err != nil {
			return err
		}
		return fmt.Errorf("intentional rollback")
	})
	if err == nil || err.Error() != "intentional rollback" {
		t.Fatalf("rollback: %v", err)
	}
	var rolledBack int64
	must(db.Table("notifications").Where("post_id=?", rollbackPost).Count(&rolledBack).Error)
	if rolledBack != 0 {
		t.Fatal("failed vote left a milestone")
	}
	_, err = postRepo.SetVote(rollbackPost, voters[9], 1)
	must(err)
	count(owner, 1)
	must(db.Exec("UPDATE posts SET is_hidden=true WHERE id=?", rollbackPost).Error)
	count(owner, 0)
	// Aggregate notifications disappear with a removed parent post, too.
	must(db.Exec("UPDATE posts SET is_deleted=true WHERE id=?", post).Error)
	count(commentOwner, 0)
	page, err = inbox.List(owner, "", 20)
	must(err)
	if len(page.Notifications) != 0 {
		t.Fatal("deleted post remains visible")
	}
}
