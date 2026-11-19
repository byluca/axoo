package posts

import (
	"axoo-server/internal/models"
	"errors"
	"github.com/google/uuid"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
	"net/url"
	"os"
	"strings"
	"sync"
	"testing"
)

func pollTestDatabase(t *testing.T) *gorm.DB {
	t.Helper()
	raw := os.Getenv("AXOO_POLL_TEST_DB_URL")
	if raw == "" {
		t.Skip("set AXOO_POLL_TEST_DB_URL for isolated PostgreSQL tests")
	}
	dsn, err := url.Parse(raw)
	if err != nil || (dsn.Hostname() != "127.0.0.1" && dsn.Hostname() != "localhost") {
		t.Fatal("poll tests require local PostgreSQL")
	}
	admin, err := gorm.Open(postgres.Open(raw), &gorm.Config{Logger: logger.Default.LogMode(logger.Silent)})
	if err != nil {
		t.Fatal(err)
	}
	schema := "poll_test_" + strings.ReplaceAll(uuid.NewString(), "-", "")
	if err := admin.Exec("CREATE SCHEMA " + schema).Error; err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { admin.Exec("DROP SCHEMA " + schema + " CASCADE"); pool, _ := admin.DB(); pool.Close() })
	query := dsn.Query()
	query.Set("search_path", schema)
	dsn.RawQuery = query.Encode()
	db, err := gorm.Open(postgres.Open(dsn.String()), &gorm.Config{Logger: logger.Default.LogMode(logger.Silent)})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { pool, _ := db.DB(); pool.Close() })
	if err := db.Exec(`CREATE TABLE users (id uuid PRIMARY KEY, avatar_config jsonb);
        CREATE TABLE posts (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid, client_id uuid,
        city_id uuid, body text NOT NULL, flair text, media jsonb, entities jsonb DEFAULT '[]',
        poll_options jsonb CHECK (jsonb_typeof(poll_options) = 'array' AND jsonb_array_length(poll_options) BETWEEN 2 AND 4),
        is_deleted boolean DEFAULT false, is_hidden boolean DEFAULT false, created_at timestamptz, updated_at timestamptz);
        CREATE TABLE user_blocks (blocker_id uuid, blocked_id uuid);
        CREATE TABLE user_hidden_posts (user_id uuid, post_id uuid);
        CREATE TABLE post_votes (post_id uuid, user_id uuid, type text);
        CREATE TABLE comments (post_id uuid, is_deleted boolean, is_hidden boolean);
        CREATE TABLE poll_votes (
            post_id uuid NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
            user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            option_id uuid NOT NULL,
            created_at timestamptz NOT NULL DEFAULT now(),
            PRIMARY KEY (post_id, user_id)
        );
        CREATE FUNCTION guard_poll_post_content() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
            IF OLD.poll_options IS NOT NULL AND (
                NEW.body IS DISTINCT FROM OLD.body OR NEW.flair IS DISTINCT FROM OLD.flair OR
                NEW.media IS DISTINCT FROM OLD.media OR NEW.entities IS DISTINCT FROM OLD.entities OR
                NEW.poll_options IS DISTINCT FROM OLD.poll_options
            ) THEN
                RAISE EXCEPTION 'I post con sondaggio non possono essere modificati';
            END IF;
            RETURN NEW;
        END $$;
        CREATE TRIGGER guard_poll_post_content BEFORE UPDATE ON posts
            FOR EACH ROW EXECUTE FUNCTION guard_poll_post_content();`).Error; err != nil {
		t.Fatal(err)
	}
	return db
}

func seedPollUser(t *testing.T, db *gorm.DB) string {
	t.Helper()
	id := uuid.NewString()
	if err := db.Exec("INSERT INTO users(id, avatar_config) VALUES (?, ?::jsonb)", id, `{"seed":"test"}`).Error; err != nil {
		t.Fatal(err)
	}
	return id
}
func seedPoll(t *testing.T, db *gorm.DB, owner string) models.Post {
	t.Helper()
	options, err := NewService(nil, nil, nil).validatePoll(owner, &models.PollInput{Options: []models.PollOptionInput{{Text: "A"}, {Text: "B"}}})
	if err != nil {
		t.Fatal(err)
	}
	post := models.Post{UserID: owner, CityID: uuid.NewString(), Body: "Domanda?", PollOptions: options}
	if _, err := NewRepository(db).Create(&post); err != nil {
		t.Fatal(err)
	}
	return post
}

func TestPollDatabaseCreateRetriesKeepOptionIdsAndRejectChangedContent(t *testing.T) {
	db := pollTestDatabase(t)
	repo := NewRepository(db)
	owner := seedPollUser(t, db)
	clientID := uuid.NewString()
	options, err := NewService(nil, nil, nil).validatePoll(owner, &models.PollInput{Options: []models.PollOptionInput{{Text: "A"}, {Text: "B"}}})
	if err != nil {
		t.Fatal(err)
	}
	first := models.Post{UserID: owner, CityID: uuid.NewString(), ClientID: &clientID, Body: "Domanda?", Entities: models.MessageEntitySlice{}, PollOptions: options}
	if created, err := repo.Create(&first); err != nil || !created {
		t.Fatalf("create failed: %v", err)
	}
	retry := first
	retry.ID = ""
	retry.PollOptions = append(models.PollOptions{}, options...)
	retry.PollOptions[0].ID, retry.PollOptions[1].ID = uuid.NewString(), uuid.NewString()
	if created, err := repo.Create(&retry); err != nil || created {
		t.Fatalf("retry duplicated or failed: %v", err)
	}
	if retry.ID != first.ID || retry.PollOptions[0].ID != first.PollOptions[0].ID {
		t.Fatal("retry changed persistent option ids")
	}
	changed := first
	changed.PollOptions = append(models.PollOptions{}, first.PollOptions...)
	changed.PollOptions[0].Text = "Changed"
	if _, err := repo.Create(&changed); !errors.Is(err, ErrIdempotencyConflict) {
		t.Fatalf("changed poll did not conflict: %v", err)
	}
}

func TestPollDatabaseVotesAreFinalIdempotentAndVisibleAcrossReads(t *testing.T) {
	db := pollTestDatabase(t)
	repo := NewRepository(db)
	owner, voter := seedPollUser(t, db), seedPollUser(t, db)
	post := seedPoll(t, db, owner)
	option := post.PollOptions[0].ID
	poll, err := repo.SetPollVote(post.ID, voter, option)
	if err != nil {
		t.Fatal(err)
	}
	if poll.TotalVotes != 1 || poll.MyOptionID != option || poll.Options[0].Votes != 1 || len(poll.Options[0].Voters) != 1 || poll.Options[0].Voters[0].AvatarConfig["seed"] != "test" {
		t.Fatalf("bad vote result: %+v", poll)
	}
	if _, err := repo.SetPollVote(post.ID, voter, option); err != nil {
		t.Fatal(err)
	}
	if _, err := repo.SetPollVote(post.ID, voter, post.PollOptions[1].ID); !errors.Is(err, ErrPollAlreadyVoted) {
		t.Fatalf("changed vote accepted: %v", err)
	}
	if _, err := repo.SetPollVote(post.ID, owner, uuid.NewString()); err == nil {
		t.Fatal("foreign option accepted")
	}
	saved, err := repo.FindByID(post.ID, voter)
	if err != nil {
		t.Fatal(err)
	}
	if saved.Poll.MyOptionID != option || saved.Poll.TotalVotes != 1 {
		t.Fatalf("vote missing from post detail: %+v", saved.Poll)
	}
	page, err := repo.FindAll("", voter, ListFilter{Mine: true, Order: "new"})
	if err != nil || len(page) != 0 {
		t.Fatalf("unexpected personal posts: %+v %v", page, err)
	}
	page, err = repo.FindAll("", owner, ListFilter{Mine: true, Order: "new"})
	if err != nil || len(page) != 1 || page[0].Poll.TotalVotes != 1 || page[0].Poll.Options[0].Votes != 0 {
		t.Fatalf("pre-vote counts leaked or lost: %+v %v", page, err)
	}
	if err := repo.Update(post.ID, "Changed", "", nil, nil); err == nil {
		t.Fatal("poll post could be edited")
	}
	if err := db.Model(&models.Post{}).Where("id = ?", post.ID).Update("body", "Bypass API").Error; err == nil {
		t.Fatal("database immutability guard missing")
	}
	if err := repo.SoftDelete(post.ID); err != nil {
		t.Fatal(err)
	}
	if _, err := repo.SetPollVote(post.ID, owner, option); err == nil {
		t.Fatal("deleted poll accepted a vote")
	}
}

func TestPollDatabaseConcurrentVotesAndBoundedAvatars(t *testing.T) {
	db := pollTestDatabase(t)
	repo := NewRepository(db)
	owner := seedPollUser(t, db)
	post := seedPoll(t, db, owner)
	voters := make([]string, 8)
	for i := range voters {
		voters[i] = seedPollUser(t, db)
	}
	var wg sync.WaitGroup
	failures := make(chan error, len(voters)*2)
	for _, voter := range voters {
		for i := 0; i < 2; i++ {
			wg.Add(1)
			go func(user string) {
				defer wg.Done()
				_, err := repo.SetPollVote(post.ID, user, post.PollOptions[0].ID)
				failures <- err
			}(voter)
		}
	}
	wg.Wait()
	close(failures)
	for err := range failures {
		if err != nil {
			t.Fatal(err)
		}
	}
	saved, err := repo.FindByID(post.ID, voters[0])
	if err != nil {
		t.Fatal(err)
	}
	if saved.Poll.TotalVotes != len(voters) || saved.Poll.Options[0].Votes != len(voters) || len(saved.Poll.Options[0].Voters) != 3 {
		t.Fatalf("duplicate votes or unbounded previews: %+v", saved.Poll)
	}
	if err := db.Exec("INSERT INTO user_blocks VALUES (?, ?)", owner, post.UserID).Error; err != nil {
		t.Fatal(err)
	}
	if _, err := repo.SetPollVote(post.ID, owner, post.PollOptions[0].ID); err == nil {
		t.Fatal("blocked post accepted a vote")
	}
}
