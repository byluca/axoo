package media

import (
	"bytes"
	"context"
	"encoding/json"
	"image"
	"image/color"
	"image/png"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	"axoo-server/internal/models"
	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
)

func stickerFixture(t *testing.T, size int) []byte {
	t.Helper()
	img := image.NewNRGBA(image.Rect(0, 0, size, size))
	img.SetNRGBA(size/2, size/2, color.NRGBA{R: 230, A: 120})
	var b bytes.Buffer
	if err := png.Encode(&b, img); err != nil {
		t.Fatal(err)
	}
	return b.Bytes()
}

func TestStickerSanitization(t *testing.T) {
	raw := append(stickerFixture(t, 512), []byte("PRIVATE_METADATA")...)
	clean, err := cleanSticker(raw)
	if err != nil {
		t.Fatal(err)
	}
	if bytes.Contains(clean, []byte("PRIVATE_METADATA")) {
		t.Fatal("metadata retained")
	}
	decoded, err := png.Decode(bytes.NewReader(clean))
	if err != nil {
		t.Fatal(err)
	}
	_, _, _, alpha := decoded.At(256, 256).RGBA()
	if alpha == 0 || alpha == 65535 {
		t.Fatal("partial transparency lost")
	}
	_, _, _, alpha = decoded.At(0, 0).RGBA()
	if alpha != 0 {
		t.Fatal("transparent pixels flattened")
	}
	for _, invalid := range [][]byte{nil, []byte("<svg/>"), stickerFixture(t, 513), stickerFixture(t, 511), clean[:30], make([]byte, MaxStickerBytes+1)} {
		if _, err := cleanSticker(invalid); err == nil {
			t.Fatal("invalid PNG accepted")
		}
	}
}

func TestPersonalStickerAttachmentValidation(t *testing.T) {
	t.Setenv("SUPABASE_URL", "https://example.test")
	valid := personalSticker("stickers/users/" + uuid.NewString() + "/" + uuid.NewString() + ".png")
	if err := ValidateRemoteAttachment(&valid); err != nil {
		t.Fatal(err)
	}
	for _, edit := range []func(*models.ImageMeta){
		func(m *models.ImageMeta) { m.URL += "?other=file" },
		func(m *models.ImageMeta) { m.URL = strings.Replace(m.URL, "example.test", "evil.test", 1) },
		func(m *models.ImageMeta) { m.URL = strings.Replace(m.URL, "/users/", "/users/../", 1) },
		func(m *models.ImageMeta) { m.ThumbnailURL = "https://evil.test/image.png" },
		func(m *models.ImageMeta) { m.ProviderID = uuid.NewString() },
		func(m *models.ImageMeta) { m.MIMEType = "image/svg+xml" },
		func(m *models.ImageMeta) { m.Type = "gif" },
		func(m *models.ImageMeta) { m.Width = 0 },
	} {
		value := valid
		edit(&value)
		if err := ValidateRemoteAttachment(&value); err == nil {
			t.Fatalf("invalid attachment accepted: %+v", value)
		}
	}
}

type stickerStoreStub struct {
	db   *gorm.DB
	path string
	data []byte
	fail bool
}

func (s *stickerStoreStub) PutSticker(_ context.Context, path string, data []byte) (string, error) {
	if s.fail {
		return "", fiber.NewError(502, "storage offline")
	}
	s.path = path
	s.data = data
	return stickerPrefix() + path, s.db.Exec("INSERT INTO storage.objects(bucket_id,name) VALUES (?,?)", StickerBucket, path).Error
}

func (s *stickerStoreStub) CopySticker(_ context.Context, source, destination string) error {
	return s.db.Exec("INSERT INTO storage.objects(bucket_id,name) SELECT bucket_id,? FROM storage.objects WHERE bucket_id = ? AND name = ?", destination, StickerBucket, source).Error
}

func stickerUploadRequest(t *testing.T, raw []byte) *http.Request {
	t.Helper()
	var b bytes.Buffer
	writer := multipart.NewWriter(&b)
	part, err := writer.CreateFormFile("sticker", "sticker.png")
	if err != nil {
		t.Fatal(err)
	}
	part.Write(raw)
	writer.Close()
	req := httptest.NewRequest("POST", "/media/stickers", &b)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	return req
}

// Opt-in isolated test DB only. All DDL and uploads are rolled back together.
func TestPersonalStickerLibraryDatabase(t *testing.T) {
	dsn := os.Getenv("AXOO_STICKER_TEST_DB_URL")
	if dsn == "" {
		t.Skip("set AXOO_STICKER_TEST_DB_URL to an empty local PostgreSQL database")
	}
	db, err := gorm.Open(postgres.Open(dsn), &gorm.Config{})
	if err != nil {
		t.Fatal(err)
	}
	sqlDB, _ := db.DB()
	defer sqlDB.Close()
	tx := db.Begin()
	if tx.Error != nil {
		t.Fatal(tx.Error)
	}
	defer tx.Rollback()
	if err := tx.Exec("CREATE SCHEMA storage").Error; err != nil {
		t.Fatal("requires an empty test database", err)
	}
	if err := tx.Exec("CREATE TABLE storage.objects(bucket_id text, name text, created_at timestamptz DEFAULT clock_timestamp(), PRIMARY KEY(bucket_id,name))").Error; err != nil {
		t.Fatal(err)
	}
	t.Setenv("SUPABASE_URL", "https://example.test")
	owner := uuid.NewString()
	other := "stickers/users/" + uuid.NewString() + "/" + uuid.NewString() + ".png"
	tx.Exec("INSERT INTO storage.objects(bucket_id,name) VALUES (?,?)", StickerBucket, other)
	store := &stickerStoreStub{db: tx}
	app := fiber.New()
	app.Use(func(c *fiber.Ctx) error { c.Locals("user_id", owner); return c.Next() })
	NewStickerHandler(tx, store).RegisterRoutes(app)
	response, err := app.Test(stickerUploadRequest(t, stickerFixture(t, 512)))
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	if response.StatusCode != 201 {
		t.Fatalf("upload: %d", response.StatusCode)
	}
	var created models.ImageMeta
	if err := json.NewDecoder(response.Body).Decode(&created); err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(store.path, "stickers/users/"+owner+"/") || created.Provider != "axoo" {
		t.Fatal("wrong owner/metadata")
	}
	if err := ValidateRemoteAttachment(&created); err != nil {
		t.Fatal(err)
	}
	listed, err := app.Test(httptest.NewRequest("GET", "/media/stickers/mine", nil))
	if err != nil {
		t.Fatal(err)
	}
	defer listed.Body.Close()
	var page struct {
		Items []models.ImageMeta `json:"items"`
	}
	if err := json.NewDecoder(listed.Body).Decode(&page); err != nil {
		t.Fatal(err)
	}
	if len(page.Items) != 1 || page.Items[0].URL != created.URL {
		t.Fatal("library leaked another owner or lost uploaded sticker")
	}
	otherSticker := personalSticker(other)
	for attempt := 0; attempt < 2; attempt++ {
		raw, _ := json.Marshal(otherSticker)
		request := httptest.NewRequest("POST", "/media/stickers/save", bytes.NewReader(raw))
		request.Header.Set("Content-Type", "application/json")
		response, err := app.Test(request)
		if err != nil {
			t.Fatal(err)
		}
		var saved models.ImageMeta
		json.NewDecoder(response.Body).Decode(&saved)
		response.Body.Close()
		if response.StatusCode != 201-attempt || saved.ProviderID != otherSticker.ProviderID || saved.URL == otherSticker.URL ||
			!strings.Contains(saved.URL, "/"+owner+"/") {
			t.Fatalf("save/repeated save: %d %+v", response.StatusCode, saved)
		}
	}
	var ownCount int64
	tx.Raw("SELECT count(*) FROM storage.objects WHERE name LIKE ?", "stickers/users/"+owner+"/%").Scan(&ownCount)
	if ownCount != 2 {
		t.Fatal("saving twice created duplicates")
	}
	store.fail = true
	failure, _ := app.Test(stickerUploadRequest(t, stickerFixture(t, 512)))
	defer failure.Body.Close()
	if failure.StatusCode != 502 {
		t.Fatalf("storage error: %d", failure.StatusCode)
	}
	store.fail = false
	for i := 2; i < MaxPersonalStickers; i++ {
		tx.Exec("INSERT INTO storage.objects(bucket_id,name) VALUES (?,?)", StickerBucket, "stickers/users/"+owner+"/"+uuid.NewString()+".png")
	}
	full, _ := app.Test(stickerUploadRequest(t, stickerFixture(t, 512)))
	defer full.Body.Close()
	if full.StatusCode != 409 {
		t.Fatalf("library limit: %d", full.StatusCode)
	}
}
