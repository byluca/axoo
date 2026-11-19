package analytics

import "github.com/gofiber/fiber/v2"

type Handler struct {
	service *Service
}

func NewHandler(service *Service) *Handler {
	return &Handler{service: service}
}

func (h *Handler) RegisterRoutes(router fiber.Router) {
	router.Post("/activity/open", h.recordOpen)
}

func (h *Handler) recordOpen(c *fiber.Ctx) error {
	if err := h.service.RecordOpen(c.Locals("user_id").(string)); err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "attività non registrata"})
	}
	return c.SendStatus(fiber.StatusNoContent)
}
