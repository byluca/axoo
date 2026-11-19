package users

import (
	"axoo-server/internal/models"
	"github.com/gofiber/fiber/v2"
)

func (r *Repository) UpdateProfilePrivacy(userID string, isPublic bool) error {
	return r.db.Model(&models.User{}).Where("id = ?", userID).Update("is_profile_public", isPublic).Error
}

func (s *Service) UpdateProfilePrivacy(userID string, isPublic bool) (*models.User, error) {
	if err := s.repo.UpdateProfilePrivacy(userID, isPublic); err != nil {
		return nil, err
	}
	return s.repo.FindByID(userID)
}

func (h *Handler) updateProfilePrivacy(c *fiber.Ctx) error {
	var body struct {
		IsProfilePublic *bool `json:"is_profile_public"`
	}
	if err := c.BodyParser(&body); err != nil || body.IsProfilePublic == nil {
		return c.Status(400).JSON(fiber.Map{"error": "visibilità del profilo mancante"})
	}
	user, err := h.service.UpdateProfilePrivacy(c.Locals("user_id").(string), *body.IsProfilePublic)
	if err != nil {
		return c.Status(500).JSON(fiber.Map{"error": "impossibile aggiornare la visibilità"})
	}
	return c.JSON(profileResponse(user))
}
