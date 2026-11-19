package users

import (
	"axoo-server/internal/models"
	"errors"

	"gorm.io/gorm"
)

type Repository struct {
	db *gorm.DB
}

func NewRepository(db *gorm.DB) *Repository {
	return &Repository{db: db}
}

func (r *Repository) FindByID(id string) (*models.User, error) {
	var user models.User
	err := r.db.Preload("University").Where("id = ?", id).First(&user).Error
	if err != nil {
		return nil, err
	}
	return &user, nil
}

func (r *Repository) FindNicknames(userIDs []string) (map[string]string, error) {
	if len(userIDs) == 0 {
		return map[string]string{}, nil
	}
	var users []models.User
	if err := r.db.Select("id, nickname").Where("id IN ?", userIDs).Find(&users).Error; err != nil {
		return nil, err
	}
	result := make(map[string]string, len(users))
	for _, user := range users {
		result[user.ID] = user.Nickname
	}
	return result, nil
}

func (r *Repository) FindUniversitiesForUsers(userIDs []string) (map[string]models.University, error) {
	if len(userIDs) == 0 {
		return map[string]models.University{}, nil
	}
	var rows []struct {
		UserID       string
		UniversityID string
		Name         string
	}
	if err := r.db.Table("users AS u").
		Select("u.id AS user_id, universities.id AS university_id, universities.name").
		Joins("JOIN universities ON universities.id = u.university_id").
		Where("u.id IN ?", userIDs).Find(&rows).Error; err != nil {
		return nil, err
	}
	result := make(map[string]models.University, len(rows))
	for _, row := range rows {
		result[row.UserID] = models.University{ID: row.UniversityID, Name: row.Name}
	}
	return result, nil
}

func (r *Repository) UpdateAvatarConfig(userID string, config models.AvatarConfig) error {
	result := r.db.Model(&models.User{}).Where("id = ?", userID).
		Where("COALESCE(avatar_config->>'official_account', '') NOT IN ('axoo', 'aup')").Update("avatar_config", config)
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected != 1 {
		return ErrOfficialAvatar
	}
	return nil
}

func (r *Repository) NicknameAvailable(userID, nickname string) (bool, error) {
	var count int64
	err := r.db.Model(&models.User{}).
		Where("nickname = ? AND id <> ?", nickname, userID).
		Count(&count).Error
	return count == 0, err
}

func (r *Repository) UpdateNickname(userID, nickname string) error {
	result := r.db.Model(&models.User{}).Where("id = ?", userID).
		Where("COALESCE(avatar_config->>'official_account', '') NOT IN ('axoo', 'aup')").Update("nickname", nickname)
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected != 1 {
		return ErrNicknameUnavailable
	}
	return nil
}

func (r *Repository) BlockUser(blockerID, blockedID string) error {
	if blockerID == blockedID {
		return gorm.ErrInvalidData
	}
	return r.db.Exec(
		`INSERT INTO user_blocks (blocker_id, blocked_id) VALUES (?, ?) ON CONFLICT DO NOTHING`,
		blockerID,
		blockedID,
	).Error
}

func (r *Repository) UnblockUser(blockerID, blockedID string) error {
	return r.db.Where("blocker_id = ? AND blocked_id = ?", blockerID, blockedID).Delete(&models.UserBlock{}).Error
}

func (r *Repository) BlockedUsers(blockerID string) ([]models.User, error) {
	var users []models.User
	err := r.db.Table("users").
		Select("users.id, users.nickname, users.avatar_config, users.university_id, users.created_at").
		Joins("JOIN user_blocks ON user_blocks.blocked_id = users.id").
		Where("user_blocks.blocker_id = ?", blockerID).
		Order("user_blocks.created_at DESC").
		Find(&users).Error
	return users, err
}

// DeleteAccount physically removes the pseudonymous account and all content tied to it.
// It is intentionally a single transaction: a partial deletion must never be visible.
func (r *Repository) DeleteAccount(userID string) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		// Serialize with profile bootstrap, including requests already in flight.
		if err := tx.Exec("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", "account:"+userID).Error; err != nil {
			return err
		}
		if err := tx.Exec("INSERT INTO account_deletions (user_id) VALUES (?) ON CONFLICT DO NOTHING", userID).Error; err != nil {
			return err
		}
		var user models.User
		if err := tx.Select("id").Where("id = ?", userID).First(&user).Error; err != nil {
			if errors.Is(err, gorm.ErrRecordNotFound) {
				return nil
			}
			return err
		}
		var postIDs, commentIDs []string
		if err := tx.Model(&models.Post{}).Where("user_id = ?", userID).Pluck("id", &postIDs).Error; err != nil {
			return err
		}
		commentsQuery := tx.Model(&models.Comment{}).Where("user_id = ?", userID)
		if len(postIDs) > 0 {
			commentsQuery = commentsQuery.Or("post_id IN ?", postIDs)
		}
		if err := commentsQuery.Pluck("id", &commentIDs).Error; err != nil {
			return err
		}
		if len(postIDs) > 0 {
			if err := tx.Where("target_type = 'post' AND target_id IN ?", postIDs).Delete(&models.Report{}).Error; err != nil {
				return err
			}
		}
		if len(commentIDs) > 0 {
			if err := tx.Where("target_type = 'comment' AND target_id IN ?", commentIDs).Delete(&models.Report{}).Error; err != nil {
				return err
			}
		}
		if len(commentIDs) > 0 {
			if err := tx.Where("id IN ?", commentIDs).Delete(&models.Comment{}).Error; err != nil {
				return err
			}
		}
		if err := tx.Where("user_id = ?", userID).Delete(&models.Post{}).Error; err != nil {
			return err
		}
		if err := tx.Where("target_type = 'user' AND target_id = ?", userID).Delete(&models.Report{}).Error; err != nil {
			return err
		}
		return tx.Delete(&user).Error
	})
}

func (r *Repository) PendingAccountDeletions() ([]string, error) {
	var ids []string
	err := r.db.Table("account_deletions").Where("completed_at IS NULL").Order("requested_at").Limit(100).Pluck("user_id", &ids).Error
	return ids, err
}

func (r *Repository) CompleteAccountDeletion(userID string) error {
	return r.db.Exec("UPDATE account_deletions SET completed_at = NOW() WHERE user_id = ?", userID).Error
}
