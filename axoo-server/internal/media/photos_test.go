package media

import (
	"axoo-server/internal/models"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"github.com/gofiber/fiber/v2"
	"image"
	"image/jpeg"
	"io"
	"mime/multipart"
	"net/http/httptest"
	"strings"
	"testing"
)

func photoFixture(t *testing.T, width, height int) []byte {
	t.Helper()
	var out bytes.Buffer
	if err := jpeg.Encode(&out, image.NewRGBA(image.Rect(0, 0, width, height)), nil); err != nil {
		t.Fatal(err)
	}
	return out.Bytes()
}

func TestPhotoSanitization(t *testing.T) {
	raw := photoFixture(t, 12, 8)
	// Inject an APP1 metadata segment; re-encoding must remove it.
	metadata := []byte("Exif\x00\x00PRIVATE_GPS_METADATA")
	segment := append([]byte{0xff, 0xe1, 0, byte(len(metadata) + 2)}, metadata...)
	raw = append(append(append([]byte{}, raw[:2]...), segment...), raw[2:]...)
	cleaned, w, h, err := cleanPhoto(raw)
	if err != nil || w != 12 || h != 8 {
		t.Fatalf("invalid result %d %d %v", w, h, err)
	}
	if bytes.Contains(cleaned, metadata) {
		t.Fatal("metadata retained")
	}
	if _, err := jpeg.Decode(bytes.NewReader(cleaned)); err != nil {
		t.Fatal(err)
	}
	for _, invalid := range [][]byte{[]byte("<svg></svg>"), raw[:20], make([]byte, MaxPhotoBytes+1), photoFixture(t, 2049, 1)} {
		if _, _, _, err := cleanPhoto(invalid); err == nil {
			t.Fatal("invalid photo accepted")
		}
	}
}

type photoStoreStub struct {
	path string
	data []byte
	fail bool
}

func (s *photoStoreStub) PutPhoto(_ context.Context, path string, data []byte) (string, error) {
	s.path, s.data = path, data
	if s.fail {
		return "", fmt.Errorf("storage unavailable")
	}
	return "https://example.test/" + path, nil
}
func (s *photoStoreStub) DeletePhoto(context.Context, string) error { return nil }

func TestPhotoUploadContract(t *testing.T) {
	for _, failure := range []bool{false, true} {
		store := &photoStoreStub{fail: failure}
		app := fiber.New()
		owner := "624f3b37-1bbc-4dad-970e-dd5862a027e0"
		app.Use(func(c *fiber.Ctx) error { c.Locals("user_id", owner); return c.Next() })
		NewPhotoHandler(store).RegisterRoutes(app)
		var body bytes.Buffer
		writer := multipart.NewWriter(&body)
		part, err := writer.CreateFormFile("photo", "photo.jpg")
		if err != nil {
			t.Fatal(err)
		}
		part.Write(photoFixture(t, 12, 8))
		writer.Close()
		request := httptest.NewRequest("POST", "/media/photos", &body)
		request.Header.Set("Content-Type", writer.FormDataContentType())
		response, err := app.Test(request)
		if err != nil {
			t.Fatal(err)
		}
		defer response.Body.Close()
		if failure {
			if response.StatusCode != 502 {
				t.Fatalf("storage failure: %d", response.StatusCode)
			}
			continue
		}
		if response.StatusCode != 201 {
			t.Fatalf("upload: %d", response.StatusCode)
		}
		var photo models.ImageMeta
		if err := json.NewDecoder(response.Body).Decode(&photo); err != nil {
			t.Fatal(err)
		}
		if photo.Type != "photo" || photo.Width != 12 || photo.Height != 8 || photo.MIMEType != "image/jpeg" || !strings.HasPrefix(store.path, owner+"/") {
			t.Fatalf("bad metadata: %+v", photo)
		}
	}
}

func TestPhotoOwnershipRejectsOtherUsersAndURLs(t *testing.T) {
	t.Setenv("SUPABASE_URL", "https://example.test")
	for _, url := range []string{
		"https://evil.test/storage/v1/object/public/post-images/me/624f3b37-1bbc-4dad-970e-dd5862a027e0.jpg",
		photoPrefix() + "other/624f3b37-1bbc-4dad-970e-dd5862a027e0.jpg",
		photoPrefix() + "me/../secret.jpg",
		photoPrefix() + "me/not-a-uuid.jpg",
	} {
		if err := ValidatePostPhoto(nil, "me", &models.ImageMeta{Type: "photo", URL: url}); err == nil {
			t.Fatalf("accepted %s", url)
		}
	}
}

func TestPhotoUploadRejectsNonImageBeforeStorage(t *testing.T) {
	store := &photoStoreStub{}
	app := fiber.New()
	app.Use(func(c *fiber.Ctx) error { c.Locals("user_id", "624f3b37-1bbc-4dad-970e-dd5862a027e0"); return c.Next() })
	NewPhotoHandler(store).RegisterRoutes(app)
	var body bytes.Buffer
	writer := multipart.NewWriter(&body)
	part, _ := writer.CreateFormFile("photo", "fake.jpg")
	io.WriteString(part, "not an image")
	writer.Close()
	request := httptest.NewRequest("POST", "/media/photos", &body)
	request.Header.Set("Content-Type", writer.FormDataContentType())
	response, err := app.Test(request)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	if response.StatusCode != 400 || store.path != "" {
		t.Fatal("invalid file reached storage")
	}
}
