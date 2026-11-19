package comments

import (
	"axoo-server/internal/models"
	"gorm.io/gorm"
)

func visiblePost(db *gorm.DB, postID, userID string) error {
	var post models.Post
	return db.Select("id").Where("id = ? AND is_deleted = false AND is_hidden = false", postID).
		Where("NOT EXISTS (SELECT 1 FROM user_blocks b WHERE b.blocker_id = ? AND b.blocked_id = posts.user_id)", userID).
		First(&post).Error
}
