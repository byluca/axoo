package moderation

import (
	"errors"

	"github.com/gofiber/fiber/v2"
)

type Handler struct{ service *Service }

func NewHandler(service *Service) *Handler { return &Handler{service: service} }

func (h *Handler) RegisterRoutes(router fiber.Router) {
	group := router.Group("/moderation")
	group.Get("/queue", h.queue)
	group.Get("/actions", h.actions)
	group.Post("/actions", h.act)
}

func (h *Handler) queue(c *fiber.Ctx) error {
	items, err := h.service.Queue(c.Locals("user_id").(string), c.Query("status", "open"))
	if err != nil {
		return moderationError(c, err)
	}
	return c.JSON(fiber.Map{"items": items})
}

func (h *Handler) actions(c *fiber.Ctx) error {
	items, err := h.service.Actions(c.Locals("user_id").(string))
	if err != nil {
		return moderationError(c, err)
	}
	return c.JSON(fiber.Map{"actions": items})
}

func (h *Handler) act(c *fiber.Ctx) error {
	var input ActionInput
	if err := c.BodyParser(&input); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "dati non validi"})
	}
	if err := h.service.Act(c.Locals("user_id").(string), input); err != nil {
		return moderationError(c, err)
	}
	return c.SendStatus(fiber.StatusNoContent)
}

func moderationError(c *fiber.Ctx, err error) error {
	if errors.Is(err, ErrForbidden) {
		return c.Status(fiber.StatusForbidden).JSON(fiber.Map{"error": err.Error()})
	}
	return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": err.Error()})
}
