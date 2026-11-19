package push

import (
	"context"
	"log"
	"strings"
	"time"
	"unicode"

	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
)

type installationStore interface {
	Register(context.Context, string, string, string) error
	Remove(context.Context, string, string, string) error
}

type Handler struct{ repo installationStore }

func NewHandler(repo installationStore) *Handler { return &Handler{repo: repo} }

func (h *Handler) RegisterRoutes(router fiber.Router) {
	router.Put("/users/me/push-installations/:installationId", h.update)
	router.Delete("/users/me/push-installations/:installationId", h.update)
}

func (h *Handler) update(c *fiber.Ctx) error {
	userID, _ := c.Locals("user_id").(string)
	if userID == "" {
		return c.SendStatus(fiber.StatusUnauthorized)
	}
	id, err := uuid.Parse(c.Params("installationId"))
	var body struct {
		Registration string `json:"registration"`
	}
	if err != nil || id == uuid.Nil || c.BodyParser(&body) != nil ||
		body.Registration == "" || len(body.Registration) > 4096 ||
		strings.IndexFunc(body.Registration, unicode.IsSpace) >= 0 {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "installazione push non valida"})
	}
	ctx, cancel := context.WithTimeout(c.UserContext(), 3*time.Second)
	defer cancel()
	if c.Method() == fiber.MethodDelete {
		err = h.repo.Remove(ctx, userID, id.String(), body.Registration)
	} else {
		err = h.repo.Register(ctx, userID, id.String(), body.Registration)
	}
	if err != nil {
		log.Print("push: installation update failed")
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "errore aggiornamento installazione push"})
	}
	return c.SendStatus(fiber.StatusNoContent)
}
