package gifs

import (
	"github.com/gofiber/fiber/v2"
	"strings"
)

// GifService definisce le operazioni esposte dall'Handler.
type GifService interface {
	Trending(limit, offset int) (map[string]any, error)
	Search(query string, limit, offset int) (map[string]any, error)
	StickersTrending(limit, offset int) (map[string]any, error)
	StickersSearch(query string, limit, offset int) (map[string]any, error)
}

type Handler struct {
	service GifService
}

func NewHandler(service GifService) *Handler {
	return &Handler{service: service}
}

func (h *Handler) RegisterRoutes(router fiber.Router) {
	router.Get("/gifs/trending", h.trending)
	router.Get("/gifs/search", h.search)
	router.Get("/stickers/trending", h.trending)
	router.Get("/stickers/search", h.search)
}

func (h *Handler) trending(c *fiber.Ctx) error {
	limit := c.QueryInt("limit", 30)
	offset := c.QueryInt("offset", 0)

	load := h.service.Trending
	if strings.Contains(c.Path(), "/stickers/") {
		load = h.service.StickersTrending
	}
	result, err := load(limit, offset)
	if err != nil {
		return c.Status(502).JSON(fiber.Map{"error": "errore chiamata Giphy"})
	}
	return c.JSON(result)
}

func (h *Handler) search(c *fiber.Ctx) error {
	q := c.Query("q")
	if q == "" {
		return c.Status(400).JSON(fiber.Map{"error": "parametro q mancante"})
	}

	limit := c.QueryInt("limit", 30)
	offset := c.QueryInt("offset", 0)

	load := h.service.Search
	if strings.Contains(c.Path(), "/stickers/") {
		load = h.service.StickersSearch
	}
	result, err := load(q, limit, offset)
	if err != nil {
		return c.Status(502).JSON(fiber.Map{"error": "errore parsing risposta Giphy"})
	}
	return c.JSON(result)
}
