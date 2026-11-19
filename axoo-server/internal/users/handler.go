package users

import (
	"axoo-server/internal/models"
	"errors"

	"github.com/gofiber/fiber/v2"
)

type Handler struct {
	service *Service
}

func NewHandler(service *Service) *Handler {
	return &Handler{service: service}
}

func (h *Handler) RegisterRoutes(router fiber.Router) {
	router.Get("/users/me", h.getMe)
	router.Get("/users/me/nickname-availability", h.nicknameAvailability)
	router.Put("/users/me/nickname", h.updateNickname)
	router.Put("/users/me/bio", h.updateBio)
	router.Put("/users/me/privacy", h.updateProfilePrivacy)
	router.Put("/users/avatar", h.updateAvatar)
	router.Get("/users/blocks", h.listBlockedUsers)
	router.Put("/users/blocks/:userId", h.blockUser)
	router.Delete("/users/blocks/:userId", h.unblockUser)
	router.Delete("/users/me", h.deleteMe)
	router.Get("/users/:userId", h.getPublicProfile)
}

func (h *Handler) nicknameAvailability(c *fiber.Ctx) error {
	available, err := h.service.NicknameAvailable(
		c.Locals("user_id").(string),
		c.Query("nickname"),
	)
	if err != nil {
		if errors.Is(err, ErrInvalidNickname) {
			return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": err.Error()})
		}
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "errore controllo nickname"})
	}
	return c.JSON(fiber.Map{"available": available})
}

func (h *Handler) updateNickname(c *fiber.Ctx) error {
	var body struct {
		Nickname string `json:"nickname"`
	}
	if err := c.BodyParser(&body); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "nickname non valido"})
	}
	user, err := h.service.UpdateNickname(c.Locals("user_id").(string), body.Nickname)
	if err != nil {
		switch {
		case errors.Is(err, ErrInvalidNickname):
			return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": err.Error()})
		case errors.Is(err, ErrNicknameUnavailable):
			return c.Status(fiber.StatusConflict).JSON(fiber.Map{"error": "nickname non disponibile"})
		default:
			return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "errore aggiornamento nickname"})
		}
	}
	return c.JSON(profileResponse(user))
}

func (h *Handler) listBlockedUsers(c *fiber.Ctx) error {
	users, err := h.service.BlockedUsers(c.Locals("user_id").(string))
	if err != nil {
		return c.Status(500).JSON(fiber.Map{"error": "errore caricamento utenti bloccati"})
	}
	return c.JSON(fiber.Map{"users": users})
}

func (h *Handler) blockUser(c *fiber.Ctx) error {
	if err := h.service.BlockUser(c.Locals("user_id").(string), c.Params("userId")); err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "impossibile bloccare utente"})
	}
	return c.SendStatus(fiber.StatusNoContent)
}

func (h *Handler) unblockUser(c *fiber.Ctx) error {
	if err := h.service.UnblockUser(c.Locals("user_id").(string), c.Params("userId")); err != nil {
		return c.Status(500).JSON(fiber.Map{"error": "impossibile sbloccare utente"})
	}
	return c.SendStatus(fiber.StatusNoContent)
}

func (h *Handler) deleteMe(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(string)
	var body struct {
		Confirmation string `json:"confirmation"`
	}
	if err := c.BodyParser(&body); err != nil || body.Confirmation != "DELETE" {
		return c.Status(400).JSON(fiber.Map{"error": "scrivi DELETE per confermare"})
	}
	if err := h.service.DeleteAccount(userID); err != nil {
		return c.Status(500).JSON(fiber.Map{"error": "errore eliminazione profilo"})
	}
	return c.SendStatus(fiber.StatusAccepted)
}

func (h *Handler) updateAvatar(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(string)
	var body struct {
		AvatarConfig models.AvatarConfig `json:"avatar_config"`
	}
	if err := c.BodyParser(&body); err != nil || body.AvatarConfig == nil {
		return c.Status(400).JSON(fiber.Map{"error": "avatar_config mancante"})
	}
	if err := h.service.UpdateAvatarConfig(userID, body.AvatarConfig); err != nil {
		if errors.Is(err, ErrOfficialAvatar) {
			return c.Status(403).JSON(fiber.Map{"error": err.Error()})
		}
		return c.Status(500).JSON(fiber.Map{"error": "errore aggiornamento avatar"})
	}
	return c.SendStatus(204)
}

func (h *Handler) getMe(c *fiber.Ctx) error {
	userID := c.Locals("user_id").(string)

	user, err := h.service.GetProfile(userID)
	if err != nil {
		return c.Status(404).JSON(fiber.Map{"error": "utente non trovato"})
	}

	return c.JSON(profileResponse(user))
}

func profileResponse(user *models.User) fiber.Map {
	return fiber.Map{
		"id":                user.ID,
		"nickname":          user.Nickname,
		"is_profile_public": user.IsProfilePublic,
		"bio":               user.Bio,
		"bio_entities":      append(models.MessageEntitySlice{}, user.BioEntities...),
		"university_id":     user.UniversityID,
		"university_name":   user.University.Name,
		"avatar_config":     user.AvatarConfig,
		"created_at":        user.CreatedAt,
		"is_moderator":      user.IsModerator,
		"is_admin":          user.IsAdmin,
	}
}
