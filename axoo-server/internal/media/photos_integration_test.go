package media

import (
	"axoo-server/internal/models"
	"context"
	"github.com/google/uuid"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"net/url"
	"os"
	"strings"
	"testing"
)

type cleanupPhotoStore struct{ deleted []string }

func (s *cleanupPhotoStore) PutPhoto(context.Context, string, []byte) (string, error) { return "", nil }
func (s *cleanupPhotoStore) DeletePhoto(_ context.Context, path string) error {
	s.deleted = append(s.deleted, path)
	return nil
}

func TestCleanupPhotosPreservesActivePostPhotos(t *testing.T) {
	raw := os.Getenv("AXOO_PHOTOS_TEST_DB_URL")
	if raw == "" {
		t.Skip("requires local PostgreSQL")
	}
	dsn, err := url.Parse(raw)
	if err != nil || (dsn.Hostname() != "localhost" && dsn.Hostname() != "127.0.0.1") {
		t.Fatal("requires local PostgreSQL")
	}
	admin, err := gorm.Open(postgres.Open(raw), &gorm.Config{})
	if err != nil {
		t.Fatal(err)
	}
	pool, _ := admin.DB()
	defer pool.Close()
	name := "photos_test_" + strings.ReplaceAll(uuid.NewString(), "-", "")
	if err := admin.Exec("CREATE DATABASE " + name).Error; err != nil {
		t.Fatal(err)
	}
	defer func() {
		if err := admin.Exec("DROP DATABASE " + name + " WITH (FORCE)").Error; err != nil {
			t.Error(err)
		}
	}()
	dsn.Path = "/" + name
	db, err := gorm.Open(postgres.Open(dsn.String()), &gorm.Config{})
	if err != nil {
		t.Fatal(err)
	}
	testPool, _ := db.DB()
	defer testPool.Close()
	if err := db.Exec(`CREATE SCHEMA storage;
 CREATE TABLE storage.objects(bucket_id text,name text,created_at timestamptz);
 CREATE TABLE public.posts(id uuid,media jsonb,is_deleted boolean);`).Error; err != nil {
		t.Fatal(err)
	}
	t.Setenv("SUPABASE_URL", "https://example.test")
	owner := uuid.NewString()
	active := owner + "/" + uuid.NewString() + ".jpg"
	deleted := owner + "/" + uuid.NewString() + ".jpg"
	orphan := owner + "/" + uuid.NewString() + ".jpg"
	for _, path := range []string{active, deleted, orphan} {
		if err := db.Exec("INSERT INTO storage.objects VALUES (?, ?, NOW()-INTERVAL '48 hours')", PhotoBucket, path).Error; err != nil {
			t.Fatal(err)
		}
	}
	for i, path := range []string{active, deleted} {
		if err := db.Exec("INSERT INTO posts VALUES (?, jsonb_build_object('url', ?::text), ?)", uuid.NewString(), photoPrefix()+path, i == 1).Error; err != nil {
			t.Fatal(err)
		}
	}
	if err := db.Transaction(func(tx *gorm.DB) error {
		return ValidatePostPhoto(tx, owner, &models.ImageMeta{URL: photoPrefix() + active, Type: "photo"})
	}); err != nil {
		t.Fatalf("existing post photo expired: %v", err)
	}
	if err := db.Transaction(func(tx *gorm.DB) error {
		return ValidatePostPhoto(tx, uuid.NewString(), &models.ImageMeta{URL: photoPrefix() + active, Type: "photo"})
	}); err == nil {
		t.Fatal("another user's post photo was accepted")
	}
	if err := db.Transaction(func(tx *gorm.DB) error {
		return ValidatePostPhoto(tx, owner, &models.ImageMeta{URL: photoPrefix() + deleted, Type: "photo"})
	}); err == nil {
		t.Fatal("old photo from a deleted post remained reusable")
	}
	store := &cleanupPhotoStore{}
	if err := CleanupPhotos(db, store); err != nil {
		t.Fatal(err)
	}
	if len(store.deleted) != 2 || !((store.deleted[0] == orphan && store.deleted[1] == deleted) || (store.deleted[0] == deleted && store.deleted[1] == orphan)) {
		t.Fatalf("cleanup must preserve active photos and remove orphaned/deleted photos: %v", store.deleted)
	}
}
