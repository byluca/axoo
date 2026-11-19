package comments

import (
	"errors"
	"os"
	"reflect"
	"strings"
	"testing"

	"axoo-server/internal/models"
	"github.com/google/uuid"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
)

// Uses only an explicitly supplied test database and rolls the isolated schema back.
func TestCommentMediaDatabase(t *testing.T) {
	dsn := os.Getenv("AXOO_COMMENT_MEDIA_TEST_DB_URL")
	if dsn == "" {
		t.Skip("set AXOO_COMMENT_MEDIA_TEST_DB_URL for PostgreSQL verification")
	}
	db, err := gorm.Open(postgres.New(postgres.Config{DSN: dsn, PreferSimpleProtocol: true}), &gorm.Config{})
	if err != nil {
		t.Fatal(err)
	}
	connection, err := db.DB()
	if err != nil {
		t.Fatal(err)
	}
	defer connection.Close()
	rollback := errors.New("rollback test schema")
	err = db.Transaction(func(tx *gorm.DB) error {
		schema := "comment_media_" + strings.ReplaceAll(uuid.NewString(), "-", "")
		if err := tx.Exec("CREATE SCHEMA " + schema).Error; err != nil {
			return err
		}
		if err := tx.Exec("SET LOCAL search_path TO " + schema).Error; err != nil {
			return err
		}
		if err := tx.Exec(`CREATE TABLE comments (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), post_id text, user_id text,
   client_id uuid, parent_comment_id uuid, body text NOT NULL, entities jsonb DEFAULT '[]', media jsonb,
   is_hidden boolean DEFAULT false, is_deleted boolean DEFAULT false, created_at timestamptz, updated_at timestamptz)`).Error; err != nil {
			return err
		}
		media := &models.ImageMeta{Type: "sticker", URL: "https://media.giphy.com/a.gif", Width: 200, Height: 100, Provider: "giphy", ProviderID: "a"}
		comment := models.Comment{PostID: "post", UserID: "owner", Body: "", Media: media}
		if err := tx.Create(&comment).Error; err != nil {
			return err
		}
		repo := NewRepository(tx)
		saved, err := repo.FindByID(comment.ID)
		if err != nil {
			return err
		}
		if !reflect.DeepEqual(saved.Media, media) || !sameCommentRequest(comment, *saved) {
			t.Fatalf("attachment did not round trip: %+v", saved.Media)
		}
		if err := repo.Update(comment.ID, "caption", nil); err != nil {
			return err
		}
		updated, err := repo.FindByID(comment.ID)
		if err != nil {
			return err
		}
		if updated.Body != "caption" || !reflect.DeepEqual(updated.Media, media) {
			t.Fatal("text edit lost attachment")
		}
		plain := models.Comment{PostID: "post", UserID: "owner", Body: "plain"}
		if err := tx.Create(&plain).Error; err != nil {
			return err
		}
		legacy, err := repo.FindByID(plain.ID)
		if err != nil {
			return err
		}
		if legacy.Media != nil {
			t.Fatal("text comment must keep a null attachment")
		}
		return rollback
	})
	if !errors.Is(err, rollback) {
		t.Fatal(err)
	}
}
