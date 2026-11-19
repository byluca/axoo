package reports

import "github.com/gofiber/fiber/v2"

type Handler struct {
	service *Service
}

func NewHandler(service *Service) *Handler {
	return &Handler{service: service}
}

func (h *Handler) RegisterRoutes(router fiber.Router) {
	router.Post("/reports", h.report)
}

type reportRequest struct {
	TargetType string `json:"target_type"` // "post" o "comment"
	TargetID   string `json:"target_id"`
	Reason     string `json:"reason"`
}

func (h *Handler) report(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(string)

	var req reportRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "dati non validi"})
	}
	if req.TargetType == "" || req.TargetID == "" {
		return c.Status(400).JSON(fiber.Map{"error": "target_type e target_id obbligatori"})
	}

	created, err := h.service.Report(userID, req.TargetType, req.TargetID, req.Reason)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"error": err.Error()})
	}
	return c.JSON(fiber.Map{"message": "segnalazione inviata", "created": created})
}
