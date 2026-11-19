package posts

import (
	"axoo-server/internal/apierror"
	"errors"
	"github.com/gofiber/fiber/v2"
	"gorm.io/gorm"
)

func (r *Repository) HidePost(postID, userID string) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		var post struct {
			ID     string
			UserID string
		}
		if err := tx.Table("posts").Select("id,user_id").Where("id = ? AND is_deleted = FALSE AND is_hidden = FALSE", postID).Take(&post).Error; err != nil {
			return err
		}
		if post.UserID == userID {
			return apierror.Invalid(errors.New("puoi eliminare i tuoi post dal menu"))
		}
		return tx.Exec("INSERT INTO user_hidden_posts (user_id,post_id) VALUES (?,?) ON CONFLICT DO NOTHING", userID, postID).Error
	})
}
func (s *Service) HidePost(postID, userID string) error { return s.repo.HidePost(postID, userID) }
func (h *Handler) hidePost(c *fiber.Ctx) error {
	if err := h.service.HidePost(c.Params("id"), c.Locals("user_id").(string)); err != nil {
		return apierror.Respond(c, err)
	}
	return c.SendStatus(fiber.StatusNoContent)
}
func excludeHiddenPosts(db *gorm.DB, userID string) *gorm.DB {
	if userID == "" {
		return db
	}
	return db.Where("NOT EXISTS (SELECT 1 FROM user_hidden_posts h WHERE h.user_id = ? AND h.post_id = posts.id)", userID)
}
