package media

import (
	"bytes"
	"context"
	"fmt"
	"image/jpeg"
	"io"
	"net/http"
	"os"
	"strings"
	"time"

	"axoo-server/internal/apierror"
	"axoo-server/internal/models"
	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/limiter"
	"github.com/google/uuid"
	"gorm.io/gorm"
)

const PhotoBucket = "post-images"
const MaxPhotoBytes = 3 * 1024 * 1024
const MaxPhotoSide = 2048

type PhotoStorage interface {
	PutPhoto(context.Context, string, []byte) (string, error)
	DeletePhoto(context.Context, string) error
}

type PhotoHandler struct{ storage PhotoStorage }

func NewPhotoHandler(storage PhotoStorage) *PhotoHandler { return &PhotoHandler{storage: storage} }
func (h *PhotoHandler) RegisterRoutes(router fiber.Router) {
	router.Post("/media/photos", limiter.New(limiter.Config{
		Max: 12, Expiration: time.Minute,
		KeyGenerator: func(c *fiber.Ctx) string { return c.Locals("user_id").(string) },
	}), h.upload)
}

func cleanPhoto(raw []byte) ([]byte, int, int, error) {
	if len(raw) == 0 || len(raw) > MaxPhotoBytes {
		return nil, 0, 0, fmt.Errorf("foto troppo grande (massimo 3 MB)")
	}
	config, err := jpeg.DecodeConfig(bytes.NewReader(raw))
	if err != nil || config.Width < 1 || config.Height < 1 || config.Width > MaxPhotoSide || config.Height > MaxPhotoSide {
		return nil, 0, 0, fmt.Errorf("foto JPEG non valida o troppo grande (massimo 2048 pixel)")
	}
	decoded, err := jpeg.Decode(bytes.NewReader(raw))
	if err != nil {
		return nil, 0, 0, fmt.Errorf("foto non leggibile")
	}
	var out bytes.Buffer
	if err := jpeg.Encode(&out, decoded, &jpeg.Options{Quality: 85}); err != nil {
		return nil, 0, 0, err
	}
	return out.Bytes(), config.Width, config.Height, nil
}

func (h *PhotoHandler) upload(c *fiber.Ctx) error {
	file, err := c.FormFile("photo")
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "foto mancante"})
	}
	if file.Size > MaxPhotoBytes {
		return c.Status(413).JSON(fiber.Map{"error": "foto troppo grande"})
	}
	reader, err := file.Open()
	if err != nil {
		return c.SendStatus(400)
	}
	defer reader.Close()
	raw, err := io.ReadAll(io.LimitReader(reader, MaxPhotoBytes+1))
	if err != nil {
		return c.SendStatus(400)
	}
	data, width, height, err := cleanPhoto(raw)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"error": err.Error()})
	}
	userID := c.Locals("user_id").(string)
	if _, err := uuid.Parse(userID); err != nil {
		return c.SendStatus(http.StatusUnauthorized)
	}
	path := userID + "/" + uuid.NewString() + ".jpg"
	ctx, cancel := context.WithTimeout(c.UserContext(), 20*time.Second)
	defer cancel()
	url, err := h.storage.PutPhoto(ctx, path, data)
	if err != nil {
		return c.Status(502).JSON(fiber.Map{"error": "caricamento foto non riuscito, riprova"})
	}
	return c.Status(201).JSON(models.ImageMeta{URL: url, Width: width, Height: height, Type: "photo", MIMEType: "image/jpeg"})
}

func photoPrefix() string {
	return strings.TrimRight(os.Getenv("SUPABASE_URL"), "/") + "/storage/v1/object/public/" + PhotoBucket + "/"
}

func lockPhoto(tx *gorm.DB, path string) error {
	return tx.Exec("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", "photo:"+path).Error
}

// Called inside the post/event transaction; shares a lock with garbage collection.
func ValidatePostPhoto(tx *gorm.DB, userID string, photo *models.ImageMeta) error {
	if photo == nil || photo.Type != "photo" {
		return nil
	}
	if !strings.HasPrefix(photo.URL, photoPrefix()) || os.Getenv("SUPABASE_URL") == "" {
		return apierror.Invalid(fmt.Errorf("foto non valida"))
	}
	path := strings.TrimPrefix(photo.URL, photoPrefix())
	parts := strings.Split(path, "/")
	if len(parts) != 2 || parts[0] != userID || !strings.HasSuffix(parts[1], ".jpg") {
		return apierror.Invalid(fmt.Errorf("foto non appartenente all'utente"))
	}
	if _, err := uuid.Parse(strings.TrimSuffix(parts[1], ".jpg")); err != nil {
		return apierror.Invalid(fmt.Errorf("foto non valida"))
	}
	if err := lockPhoto(tx, path); err != nil {
		return err
	}
	var exists bool
	eventClause := ""
	if tx.Migrator().HasColumn("campus_events", "poster") {
		eventClause = " OR EXISTS(SELECT 1 FROM public.campus_events WHERE poster->>'url' = ?)"
	}
	args := []any{PhotoBucket, path, photo.URL}
	if eventClause != "" {
		args = append(args, photo.URL)
	}
	err := tx.Raw(`SELECT EXISTS(SELECT 1 FROM storage.objects WHERE bucket_id = ? AND name = ? AND
 (created_at > NOW() - INTERVAL '24 hours' OR EXISTS(SELECT 1 FROM public.posts WHERE media->>'url' = ? AND is_deleted = false) `+eventClause+`))`, args...).Scan(&exists).Error
	if err != nil {
		return err
	}
	if !exists {
		return apierror.Invalid(fmt.Errorf("foto scaduta: selezionala nuovamente dalla galleria"))
	}
	return nil
}

// Unattached uploads and photos from deleted posts/events are cleaned in batches.
func CleanupPhotos(db *gorm.DB, storage PhotoStorage) error {
	var paths []string
	eventClause := ""
	hasPosters := db.Migrator().HasColumn("campus_events", "poster")
	args := []any{PhotoBucket, photoPrefix()}
	if hasPosters {
		eventClause = " AND NOT EXISTS(SELECT 1 FROM public.campus_events WHERE poster->>'url' = ? || o.name)"
		args = append(args, photoPrefix())
	}
	if err := db.Raw(`SELECT name FROM storage.objects o WHERE bucket_id = ? AND created_at < NOW() - INTERVAL '24 hours'
 AND NOT EXISTS(SELECT 1 FROM public.posts WHERE media->>'url' = ? || o.name AND is_deleted = false)
 `+eventClause+` ORDER BY created_at LIMIT 20`, args...).Scan(&paths).Error; err != nil {
		return err
	}
	for _, path := range paths {
		if err := db.Transaction(func(tx *gorm.DB) error {
			if err := lockPhoto(tx, path); err != nil {
				return err
			}
			var count int64
			if err := tx.Model(&models.Post{}).Where("media->>'url' = ? AND is_deleted = false", photoPrefix()+path).Count(&count).Error; err != nil {
				return err
			}
			if count > 0 {
				return nil
			}
			if hasPosters {
				if err := tx.Table("campus_events").Where("poster->>'url' = ?", photoPrefix()+path).Count(&count).Error; err != nil {
					return err
				}
				if count > 0 {
					return nil
				}
			}
			ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
			defer cancel()
			return storage.DeletePhoto(ctx, path)
		}); err != nil {
			return err
		}
	}
	return nil
}
