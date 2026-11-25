package posts

import (
	"axoo-server/internal/apierror"
	"errors"
	"log"
	"time"

	"axoo-server/internal/analytics"
	"axoo-server/internal/models"

	"github.com/gofiber/fiber/v2"
)

// PostService definisce le operazioni di business logic esposte all'Handler.
type PostService interface {
	HidePost(postID, userID string) error
	ListPosts(userID, order, cursor string, limit, offset int) (*PaginatedPosts, error)
	ListMyPosts(userID, cursor string, limit int) (*PaginatedPosts, error)
	ListUserPosts(viewerID, authorID, cursor string, limit int) (*PaginatedPosts, error)
	GetPost(id, userID string) (*models.Post, error)
	CreatePost(userID, clientID, body, flair string, media *models.ImageMeta, entities []models.MessageEntity, poll ...*models.PollInput) (*models.Post, bool, error)
	VotePoll(postID, userID, optionID string) (*models.PostPoll, error)
	UpdatePost(postID, userID, body, flair string, media *models.ImageMeta, entities []models.MessageEntity) error
	SoftDeletePost(postID, userID string) error
	VotePost(postID, userID string, value int) (*models.VoteResult, error)
	NewFeedUnreadCount(userID string) (int, error)
	MarkNewFeedSeen(userID string, seenThrough time.Time) error
}

type Handler struct {
	service  PostService
	recorder analytics.ActionRecorder
}

func NewHandler(service PostService, recorders ...analytics.ActionRecorder) *Handler {
	handler := &Handler{service: service}
	if len(recorders) > 0 {
		handler.recorder = recorders[0]
	}
	return handler
}

func (h *Handler) RegisterRoutes(router fiber.Router) {
	router.Get("/users/:userId/posts", h.listUserPosts)
	posts := router.Group("/posts")
	posts.Get("/", h.listPosts)
	posts.Post("/", h.createPost)
	posts.Get("/new/unread-count", h.newFeedUnreadCount)
	posts.Post("/new/seen", h.markNewFeedSeen)
	posts.Get("/mine", h.listMyPosts)
	posts.Get("/:id", h.getPost)
	posts.Patch("/:id", h.updatePost)
	posts.Delete("/:id", h.deletePost)
	posts.Put("/:id/vote", h.votePost)
	posts.Put("/:id/poll/vote", h.votePoll)
	posts.Put("/:id/hidden", h.hidePost)
}

func (h *Handler) newFeedUnreadCount(c *fiber.Ctx) error {
	count, err := h.service.NewFeedUnreadCount(c.Locals("user_id").(string))
	if err != nil {
		return c.Status(500).JSON(fiber.Map{"error": "errore conteggio nuovi post"})
	}
	return c.JSON(fiber.Map{"unread_count": count})
}

func (h *Handler) markNewFeedSeen(c *fiber.Ctx) error {
	var body struct {
		SeenThrough time.Time `json:"seen_through"`
	}
	if err := c.BodyParser(&body); err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "data di lettura obbligatoria"})
	}
	if err := h.service.MarkNewFeedSeen(c.Locals("user_id").(string), body.SeenThrough); err != nil {
		return apierror.Respond(c, err)
	}
	return c.SendStatus(fiber.StatusNoContent)
}

func (h *Handler) listPosts(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(string)
	result, err := h.service.ListPosts(
		userID,
		c.Query("order", "hot"),
		c.Query("cursor"),
		c.QueryInt("limit", 20),
		c.QueryInt("offset", 0),
	)
	if err != nil {
		return apierror.Respond(c, err)
	}

	return c.JSON(fiber.Map{
		"posts":       result.Posts,
		"limit":       result.Limit,
		"has_more":    result.HasMore,
		"next_cursor": result.NextCursor,
	})
}

func (h *Handler) listUserPosts(c *fiber.Ctx) error {
	result, err := h.service.ListUserPosts(c.Locals("user_id").(string), c.Params("userId"), c.Query("cursor"), c.QueryInt("limit", 20))
	if err != nil {
		return apierror.Respond(c, err)
	}
	return c.JSON(result)
}

func (h *Handler) listMyPosts(c *fiber.Ctx) error {
	result, err := h.service.ListMyPosts(c.Locals("user_id").(string), c.Query("cursor"), c.QueryInt("limit", 20))
	if err != nil {
		return apierror.Respond(c, err)
	}
	return c.JSON(result)
}

func (h *Handler) getPost(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(string)
	post, err := h.service.GetPost(c.Params("id"), userID)
	if err != nil {
		return apierror.Respond(c, err)
	}
	return c.JSON(fiber.Map{"post": post})
}

type updatePostRequest struct {
	Body     string                 `json:"body"`
	Flair    string                 `json:"flair"`
	Media    *models.ImageMeta      `json:"media"`
	Entities []models.MessageEntity `json:"entities"`
}

func (h *Handler) updatePost(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(string)

	var req updatePostRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "dati non validi"})
	}

	if err := h.service.UpdatePost(c.Params("id"), userID, req.Body, req.Flair, req.Media, req.Entities); err != nil {
		return apierror.Respond(c, err)
	}
	return c.JSON(fiber.Map{"message": "post aggiornato"})
}

func (h *Handler) deletePost(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(string)

	if err := h.service.SoftDeletePost(c.Params("id"), userID); err != nil {
		if err.Error() == "non autorizzato" {
			return c.Status(403).JSON(fiber.Map{"error": "non autorizzato"})
		}
		return c.Status(404).JSON(fiber.Map{"error": err.Error()})
	}
	return c.JSON(fiber.Map{"message": "post eliminato"})
}

type createPostRequest struct {
	Poll     *models.PollInput      `json:"poll"`
	ClientID string                 `json:"client_id"`
	Body     string                 `json:"body"`
	Flair    string                 `json:"flair"`
	Media    *models.ImageMeta      `json:"media"`
	Entities []models.MessageEntity `json:"entities"`
}

func (h *Handler) createPost(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(string)

	var req createPostRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "dati non validi"})
	}
	if req.Body == "" {
		return c.Status(400).JSON(fiber.Map{"error": "testo obbligatorio"})
	}

	post, created, err := h.service.CreatePost(userID, req.ClientID, req.Body, req.Flair, req.Media, req.Entities, req.Poll)
	if err != nil {
		log.Printf(
			"create_post request_id=%s rejected=%q",
			c.GetRespHeader(fiber.HeaderXRequestID),
			err.Error(),
		)
		if errors.Is(err, ErrIdempotencyConflict) {
			return c.Status(409).JSON(fiber.Map{"error": err.Error()})
		}
		return c.Status(400).JSON(fiber.Map{"error": err.Error()})
	}
	status := 200
	if created {
		status = 201
		analytics.RecordActionBestEffort(h.recorder, userID, analytics.ActionPost)
	}
	return c.Status(status).JSON(fiber.Map{"post": post, "created": created})
}

func (h *Handler) votePost(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(string)

	var req models.VoteRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "dati non validi"})
	}

	value, ok := req.DesiredValue()
	if !ok {
		return c.Status(400).JSON(fiber.Map{"error": "value deve essere -1, 0 o 1"})
	}
	result, err := h.service.VotePost(c.Params("id"), userID, value)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"error": err.Error()})
	}
	if result.Changed {
		analytics.RecordActionBestEffort(h.recorder, userID, analytics.ActionVote)
	}
	return c.JSON(fiber.Map{"vote": result})
}
