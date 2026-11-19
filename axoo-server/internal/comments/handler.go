package comments

import (
	"axoo-server/internal/apierror"
	"errors"

	"axoo-server/internal/analytics"
	"axoo-server/internal/models"
	appmiddleware "axoo-server/pkg/middleware"
	"github.com/gofiber/fiber/v2"
)

type Handler struct {
	service  *Service
	recorder analytics.ActionRecorder
}

func NewHandler(service *Service, recorders ...analytics.ActionRecorder) *Handler {
	handler := &Handler{service: service}
	if len(recorders) > 0 {
		handler.recorder = recorders[0]
	}
	return handler
}

func (h *Handler) RegisterRoutes(router fiber.Router) {
	comments := router.Group("/posts/:postId/comments")
	comments.Get("/", h.listComments)
	comments.Post("/", h.createComment)
	comments.Patch("/:commentId", h.updateComment)
	comments.Delete("/:commentId", h.deleteComment)
	comments.Put("/:commentId/vote", h.voteComment)
}

func (h *Handler) listComments(c *fiber.Ctx) error {
	list, err := h.service.ListComments(c.Params("postId"), c.Locals("user_id").(string))
	if err != nil {
		return c.Status(500).JSON(fiber.Map{"error": "errore caricamento commenti"})
	}
	return c.JSON(fiber.Map{"comments": list})
}

type createCommentRequest struct {
	Media            *models.ImageMeta      `json:"media"`
	ClientID         string                 `json:"client_id"`
	Body             string                 `json:"body"`
	Entities         []models.MessageEntity `json:"entities"`
	ReplyToCommentID string                 `json:"reply_to_comment_id"`
}

func (h *Handler) createComment(c *fiber.Ctx) error {
	var req createCommentRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "dati non validi"})
	}
	if req.Body == "" && req.Media == nil {
		return c.Status(400).JSON(fiber.Map{"error": "body obbligatorio"})
	}
	comment, created, err := h.service.CreateComment(
		c.Locals("user_id").(string),
		c.Params("postId"),
		req.ClientID,
		req.Body,
		req.Entities,
		req.ReplyToCommentID,
		req.Media,
	)
	if err != nil {
		if errors.Is(err, ErrIdempotencyConflict) {
			return c.Status(fiber.StatusConflict).JSON(fiber.Map{"error": err.Error()})
		}
		return apierror.Respond(c, err)
	}
	status := fiber.StatusOK
	if created {
		status = fiber.StatusCreated
		analytics.RecordActionBestEffort(h.recorder, c.Locals("user_id").(string), analytics.ActionComment)
	} else {
		appmiddleware.RecordCommentDeduplication()
	}
	return c.Status(status).JSON(fiber.Map{"comment": comment, "created": created})
}

func (h *Handler) updateComment(c *fiber.Ctx) error {
	var req struct {
		Body     string                 `json:"body"`
		Entities []models.MessageEntity `json:"entities"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "body obbligatorio"})
	}
	err := h.service.UpdateComment(c.Params("commentId"), c.Locals("user_id").(string), req.Body, req.Entities)
	if err != nil {
		return apierror.Respond(c, err)
	}
	return c.JSON(fiber.Map{"message": "commento aggiornato"})
}

func (h *Handler) deleteComment(c *fiber.Ctx) error {
	err := h.service.DeleteComment(c.Params("commentId"), c.Locals("user_id").(string))
	if err != nil {
		if err.Error() == "non autorizzato" {
			return c.Status(403).JSON(fiber.Map{"error": err.Error()})
		}
		return c.Status(404).JSON(fiber.Map{"error": err.Error()})
	}
	return c.SendStatus(fiber.StatusNoContent)
}

type voteRequest struct {
	Value *int   `json:"value"`
	Type  string `json:"type"`
}

func (h *Handler) voteComment(c *fiber.Ctx) error {
	var req voteRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "dati non validi"})
	}
	value, ok := desiredVote(req.Value, req.Type)
	if !ok {
		return c.Status(400).JSON(fiber.Map{"error": "value deve essere -1, 0 o 1"})
	}
	result, err := h.service.VoteComment(c.Params("commentId"), c.Locals("user_id").(string), value)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"error": err.Error()})
	}
	if result.Changed {
		analytics.RecordActionBestEffort(h.recorder, c.Locals("user_id").(string), analytics.ActionVote)
	}
	return c.JSON(fiber.Map{"vote": result})
}

func desiredVote(value *int, legacyType string) (int, bool) {
	if value != nil {
		return *value, *value >= -1 && *value <= 1
	}
	if legacyType == "upvote" {
		return 1, true
	}
	if legacyType == "downvote" {
		return -1, true
	}
	return 0, false
}
