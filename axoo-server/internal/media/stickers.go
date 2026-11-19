package media

import (
	"bytes"
	"context"
	"errors"
	"image/png"
	"io"
	"os"
	"strings"
	"time"

	"axoo-server/internal/models"
	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/limiter"
	"github.com/google/uuid"
	"gorm.io/gorm"
)

const StickerBucket = "memes"
const MaxStickerBytes = 2 * 1024 * 1024
const MaxPersonalStickers = 100

type StickerStorage interface {
	PutSticker(context.Context, string, []byte) (string, error)
	CopySticker(context.Context, string, string) error
}

type StickerHandler struct {
	db      *gorm.DB
	storage StickerStorage
}

func NewStickerHandler(db *gorm.DB, storage StickerStorage) *StickerHandler {
	return &StickerHandler{db: db, storage: storage}
}

func (h *StickerHandler) RegisterRoutes(router fiber.Router) {
	router.Get("/media/stickers/mine", h.list)
	router.Post("/media/stickers/save", limiter.New(limiter.Config{Max: 20, Expiration: time.Minute,
		KeyGenerator: func(c *fiber.Ctx) string { return c.Locals("user_id").(string) }}), h.save)
	router.Post("/media/stickers", limiter.New(limiter.Config{Max: 8, Expiration: time.Minute,
		KeyGenerator: func(c *fiber.Ctx) string { return c.Locals("user_id").(string) }}), h.upload)
}

func stickerPrefix() string {
	return strings.TrimRight(os.Getenv("SUPABASE_URL"), "/") + "/storage/v1/object/public/" + StickerBucket + "/"
}

func personalSticker(path string) models.ImageMeta {
	id := strings.TrimSuffix(path[strings.LastIndex(path, "/")+1:], ".png")
	return models.ImageMeta{URL: stickerPrefix() + path, Width: 512, Height: 512,
		Type: "sticker", MIMEType: "image/png", Provider: "axoo", ProviderID: id}
}

// Storage already records the owner directory and creation date: no duplicate catalog table.
func (h *StickerHandler) list(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(string)
	if _, err := uuid.Parse(userID); err != nil {
		return c.SendStatus(401)
	}
	var paths []string
	if err := h.db.WithContext(c.UserContext()).Raw(`SELECT name FROM storage.objects
        WHERE bucket_id = ? AND name LIKE ? ORDER BY created_at DESC, name DESC LIMIT ?`,
		StickerBucket, "stickers/users/"+userID+"/%", MaxPersonalStickers).Scan(&paths).Error; err != nil {
		return c.Status(503).JSON(fiber.Map{"error": "sticker non disponibili, riprova"})
	}
	items := make([]models.ImageMeta, 0, len(paths))
	for _, path := range paths {
		item := personalSticker(path)
		if validatePersonalSticker(&item) == nil {
			items = append(items, item)
		}
	}
	return c.JSON(fiber.Map{"items": items})
}

func cleanSticker(raw []byte) ([]byte, error) {
	if len(raw) == 0 || len(raw) > MaxStickerBytes {
		return nil, errors.New("sticker troppo grande")
	}
	config, err := png.DecodeConfig(bytes.NewReader(raw))
	if err != nil || config.Width != 512 || config.Height != 512 {
		return nil, errors.New("usa uno sticker PNG da 512 pixel")
	}
	decoded, err := png.Decode(bytes.NewReader(raw))
	if err != nil {
		return nil, errors.New("sticker non leggibile")
	}
	var out bytes.Buffer
	if err := png.Encode(&out, decoded); err != nil {
		return nil, err
	}
	return out.Bytes(), nil
}

func (h *StickerHandler) upload(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(string)
	if _, err := uuid.Parse(userID); err != nil {
		return c.SendStatus(401)
	}
	file, err := c.FormFile("sticker")
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "sticker mancante"})
	}
	if file.Size > MaxStickerBytes {
		return c.SendStatus(413)
	}
	reader, err := file.Open()
	if err != nil {
		return c.SendStatus(400)
	}
	defer reader.Close()
	raw, err := io.ReadAll(io.LimitReader(reader, MaxStickerBytes+1))
	if err != nil {
		return c.SendStatus(400)
	}
	data, err := cleanSticker(raw)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"error": err.Error()})
	}
	path := "stickers/users/" + userID + "/" + uuid.NewString() + ".png"
	return h.store(c, userID, path, func(ctx context.Context) error {
		_, err := h.storage.PutSticker(ctx, path, data)
		return err
	})
}

func (h *StickerHandler) save(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(string)
	if _, err := uuid.Parse(userID); err != nil {
		return c.SendStatus(401)
	}
	var item models.ImageMeta
	if err := c.BodyParser(&item); err != nil || item.Provider != "axoo" || validatePersonalSticker(&item) != nil {
		return c.Status(400).JSON(fiber.Map{"error": "sticker non valido"})
	}
	source := strings.TrimPrefix(item.URL, stickerPrefix())
	destination := "stickers/users/" + userID + "/" + item.ProviderID + ".png"
	return h.store(c, userID, destination, func(ctx context.Context) error {
		return h.storage.CopySticker(ctx, source, destination)
	})
}

func (h *StickerHandler) store(c *fiber.Ctx, userID, path string, put func(context.Context) error) error {
	ctx, cancel := context.WithTimeout(c.UserContext(), 20*time.Second)
	defer cancel()
	existed := false
	// Uploads and saves share an account lock; saving the same sticker is idempotent.
	err := h.db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		if err := tx.Exec("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", "stickers:"+userID).Error; err != nil {
			return err
		}
		if err := tx.Raw("SELECT EXISTS(SELECT 1 FROM storage.objects WHERE bucket_id = ? AND name = ?)",
			StickerBucket, path).Scan(&existed).Error; err != nil {
			return err
		}
		if existed {
			return nil
		}
		var count int64
		if err := tx.Raw("SELECT count(*) FROM storage.objects WHERE bucket_id = ? AND name LIKE ?",
			StickerBucket, "stickers/users/"+userID+"/%").Scan(&count).Error; err != nil {
			return err
		}
		if count >= MaxPersonalStickers {
			return fiber.NewError(409, "La tua raccolta contiene già 100 sticker")
		}
		return put(ctx)
	})
	if err != nil {
		var failure *fiber.Error
		if errors.As(err, &failure) {
			return c.Status(failure.Code).JSON(fiber.Map{"error": failure.Message})
		}
		return c.Status(502).JSON(fiber.Map{"error": "salvataggio sticker non riuscito, riprova"})
	}
	status := 201
	if existed {
		status = 200
	}
	return c.Status(status).JSON(personalSticker(path))
}

func validatePersonalSticker(item *models.ImageMeta) error {
	invalid := errors.New("sticker personale non valido")
	if os.Getenv("SUPABASE_URL") == "" || item.Type != "sticker" || item.MIMEType != "image/png" ||
		item.Width != 512 || item.Height != 512 || !strings.HasPrefix(item.URL, stickerPrefix()+"stickers/users/") {
		return invalid
	}
	path := strings.TrimPrefix(item.URL, stickerPrefix()+"stickers/users/")
	parts := strings.Split(path, "/")
	if len(parts) != 2 || parts[1] != item.ProviderID+".png" {
		return invalid
	}
	for _, id := range []string{parts[0], item.ProviderID} {
		parsed, err := uuid.Parse(id)
		if err != nil || parsed.String() != id {
			return invalid
		}
	}
	if (item.ThumbnailURL != "" && item.ThumbnailURL != item.URL) ||
		(item.PreviewFallbackURL != "" && item.PreviewFallbackURL != item.URL) {
		return invalid
	}
	return nil
}
