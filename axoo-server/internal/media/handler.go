package media

import (
	"strconv"

	"github.com/gofiber/fiber/v2"
)

// MemeItem rappresenta un meme e la sua anteprima sfuocata.
type MemeItem struct {
	URL                string `json:"url"`
	GridPreviewURL     string `json:"grid_preview_url"`
	PreviewFallbackURL string `json:"preview_fallback_url"`
	Width              int    `json:"width"`
	Height             int    `json:"height"`
}

type MemePage struct {
	Items      []MemeItem
	NextOffset *int
}

// MemeLister astrae la lista dei meme dallo storage.
type MemeLister interface {
	ListMemes(category string, offset, limit int) (MemePage, error)
}

type Handler struct {
	memeLister MemeLister
}

func NewHandler(memeLister MemeLister) *Handler {
	return &Handler{memeLister: memeLister}
}

func (h *Handler) RegisterRoutes(router fiber.Router) {
	router.Get("/memes", h.listMemes)
}

var validMemeCategories = map[string]bool{
	"italia":     true,
	"world":      true,
	"tv":         true,
	"musica":     true,
	"videogames": true,
	"sport":      true,
}

func (h *Handler) listMemes(c *fiber.Ctx) error {
	category := c.Query("category", "italia")

	if !validMemeCategories[category] {
		return c.Status(400).JSON(fiber.Map{"error": "categoria non valida"})
	}

	limit := parseBoundedInt(c.Query("limit"), 30, 1, 50)
	offset := parseBoundedInt(c.Query("offset"), 0, 0, 10_000)
	page, err := h.memeLister.ListMemes(category, offset, limit)
	if err != nil {
		return c.Status(500).JSON(fiber.Map{"error": "errore caricamento meme"})
	}

	c.Set(fiber.HeaderCacheControl, "public, max-age=300, stale-while-revalidate=86400")
	return c.JSON(fiber.Map{"memes": page.Items, "next_offset": page.NextOffset})
}

func parseBoundedInt(raw string, fallback, minimum, maximum int) int {
	value, err := strconv.Atoi(raw)
	if err != nil || value < minimum {
		return fallback
	}
	if value > maximum {
		return maximum
	}
	return value
}
