package moderation

import (
	"errors"
	"time"

	"axoo-server/internal/models"

	"gorm.io/gorm"
)

type Repository struct {
	db *gorm.DB
}

func NewRepository(db *gorm.DB) *Repository { return &Repository{db: db} }

type Target struct {
	Type           string
	ID             string
	AuthorID       string
	AuthorNickname string
	Body           string
	IsHidden       bool
	IsDeleted      bool
	CreatedAt      time.Time
	CityID         string
	IsRestricted   bool
}

func (r *Repository) ModeratorCityIDs(userID string) ([]string, error) {
	var cityIDs []string
	err := r.db.Table("users AS u").
		Select("universities.city_id").
		Joins("JOIN universities ON universities.id = u.university_id").
		Where("u.id = ? AND u.is_moderator = TRUE", userID).
		Pluck("universities.city_id", &cityIDs).Error
	return cityIDs, err
}

func (r *Repository) ListOpenReports(cityIDs []string, limit int) ([]models.Report, error) {
	if limit <= 0 || limit > 200 {
		limit = 100
	}
	if len(cityIDs) == 0 {
		return []models.Report{}, nil
	}
	var result []models.Report
	err := r.db.
		Where("moderation_action_id IS NULL").
		Where(`
			(target_type = 'post' AND EXISTS (
				SELECT 1 FROM posts p
				WHERE p.id = reports.target_id AND p.city_id IN ?
			)) OR
			(target_type = 'comment' AND EXISTS (
				SELECT 1 FROM comments c
				JOIN posts p ON p.id = c.post_id
				WHERE c.id = reports.target_id AND p.city_id IN ?
			))
			OR (target_type = 'user' AND EXISTS (
				SELECT 1 FROM users u JOIN universities uni ON uni.id = u.university_id
				WHERE u.id = reports.target_id AND uni.city_id IN ?
			))
		`, cityIDs, cityIDs, cityIDs).
		Order("created_at ASC").
		Limit(limit).
		Find(&result).Error
	return result, err
}

func (r *Repository) FindTarget(targetType, targetID string) (*Target, error) {
	if targetType == "user" {
		return r.FindUserTarget(targetID)
	}
	result := &Target{Type: targetType, ID: targetID}
	switch targetType {
	case "post":
		var post models.Post
		if err := r.db.Select("id", "user_id", "body", "is_hidden", "is_deleted", "created_at", "city_id").
			Where("id = ?", targetID).First(&post).Error; err != nil {
			return nil, err
		}
		result.AuthorID = post.UserID
		result.Body = post.Body
		result.IsHidden = post.IsHidden
		result.IsDeleted = post.IsDeleted
		result.CreatedAt = post.CreatedAt
		result.CityID = post.CityID
	case "comment":
		var comment models.Comment
		if err := r.db.Select("id", "post_id", "user_id", "body", "is_hidden", "is_deleted", "created_at").
			Where("id = ?", targetID).First(&comment).Error; err != nil {
			return nil, err
		}
		result.AuthorID = comment.UserID
		result.Body = comment.Body
		result.IsHidden = comment.IsHidden
		result.IsDeleted = comment.IsDeleted
		result.CreatedAt = comment.CreatedAt
		var post models.Post
		if err := r.db.Select("city_id").Where("id = ?", comment.PostID).First(&post).Error; err == nil {
			result.CityID = post.CityID
		}
	default:
		return nil, gorm.ErrRecordNotFound
	}
	var user models.User
	if err := r.db.Select("nickname").Where("id = ?", result.AuthorID).First(&user).Error; err != nil {
		return nil, err
	}
	result.AuthorNickname = user.Nickname
	return result, nil
}

func (r *Repository) FindUserTarget(userID string) (*Target, error) {
	var row struct {
		ID             string
		Nickname       string
		Bio            string
		CreatedAt      time.Time
		CityID         string
		IsBanned       bool
		SuspendedUntil *time.Time
	}
	err := r.db.Table("users AS u").
		Select("u.id, u.nickname, u.bio, u.created_at, u.is_banned, u.suspended_until, universities.city_id").
		Joins("JOIN universities ON universities.id = u.university_id").
		Where("u.id = ?", userID).
		Take(&row).Error
	if err != nil {
		return nil, err
	}
	return &Target{
		Type: "user", ID: row.ID,
		AuthorID: row.ID, AuthorNickname: row.Nickname,
		Body:      row.Bio,
		CreatedAt: row.CreatedAt, CityID: row.CityID,
		IsRestricted: row.IsBanned || (row.SuspendedUntil != nil && row.SuspendedUntil.After(time.Now())),
	}, nil
}

func (r *Repository) FindActionSource(actionID string) (*Target, error) {
	var report models.Report
	if err := r.db.Where("moderation_action_id = ?", actionID).
		Order("created_at DESC").First(&report).Error; err != nil {
		return nil, err
	}
	return r.FindTarget(report.TargetType, report.TargetID)
}

func (r *Repository) ReportsForAction(actionID string) ([]models.Report, error) {
	var reports []models.Report
	err := r.db.Where("moderation_action_id = ?", actionID).
		Order("created_at DESC").Find(&reports).Error
	return reports, err
}

func (r *Repository) Apply(moderatorID string, input ActionInput) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		switch input.Action {
		case ActionHide, ActionRestore:
			if err := applyContentVisibility(tx, input.TargetType, input.TargetID, input.Action == ActionHide); err != nil {
				return err
			}
			// Una nuova decisione sullo stesso contenuto sostituisce qualsiasi
			// avviso precedente non ancora mostrato all'autore.
			if err := tx.Model(&models.ModerationAction{}).
				Where("action = ? AND target_type = ? AND target_id = ? AND author_seen_at IS NULL", ActionHide, input.TargetType, input.TargetID).
				Update("author_seen_at", time.Now().UTC()).Error; err != nil {
				return err
			}
		case ActionKeep:
			// No content mutation: the reports are simply closed below.
		case ActionSuspend:
			until := time.Now().UTC().Add(time.Duration(input.DurationHours) * time.Hour)
			result := tx.Model(&models.User{}).Where("id = ?", input.TargetID).Updates(map[string]any{
				"suspended_until":   until,
				"suspension_reason": input.Reason,
				"is_banned":         false,
			})
			if result.Error != nil || result.RowsAffected != 1 {
				if result.Error != nil {
					return result.Error
				}
				return gorm.ErrRecordNotFound
			}
		case ActionBan:
			result := tx.Model(&models.User{}).Where("id = ?", input.TargetID).Updates(map[string]any{
				"suspended_until":   nil,
				"suspension_reason": input.Reason,
				"is_banned":         true,
			})
			if result.Error != nil || result.RowsAffected != 1 {
				if result.Error != nil {
					return result.Error
				}
				return gorm.ErrRecordNotFound
			}
		case ActionUnsuspend:
			result := tx.Model(&models.User{}).Where("id = ?", input.TargetID).Updates(map[string]any{
				"suspended_until":   nil,
				"suspension_reason": "",
				"is_banned":         false,
			})
			if result.Error != nil || result.RowsAffected != 1 {
				if result.Error != nil {
					return result.Error
				}
				return gorm.ErrRecordNotFound
			}
		default:
			return errors.New("azione non valida")
		}

		duration := input.DurationHours
		moderator := moderatorID
		audit := models.ModerationAction{
			ModeratorID: &moderator,
			Action:      input.Action,
			TargetType:  input.TargetType,
			TargetID:    input.TargetID,
			Reason:      input.Reason,
		}
		if input.Action == ActionSuspend {
			audit.DurationHours = &duration
		}
		if err := tx.Create(&audit).Error; err != nil {
			return err
		}

		reportTargetType, reportTargetID := input.TargetType, input.TargetID
		if input.Action == ActionSuspend || input.Action == ActionBan {
			reportTargetType, reportTargetID = input.SourceTargetType, input.SourceTargetID
		}
		if input.Action == ActionHide || input.Action == ActionKeep || input.Action == ActionSuspend || input.Action == ActionBan {
			return tx.Model(&models.Report{}).
				Where("target_type = ? AND target_id = ? AND moderation_action_id IS NULL", reportTargetType, reportTargetID).
				Update("moderation_action_id", audit.ID).Error
		}
		return nil
	})
}

func applyContentVisibility(tx *gorm.DB, targetType, targetID string, hidden bool) error {
	switch targetType {
	case "post":
		result := tx.Model(&models.Post{}).Where("id = ? AND is_deleted = false", targetID).
			Update("is_hidden", hidden)
		if result.Error != nil || result.RowsAffected != 1 {
			if result.Error != nil {
				return result.Error
			}
			return gorm.ErrRecordNotFound
		}
		return nil
	case "comment":
		result := tx.Model(&models.Comment{}).Where("id = ? AND is_deleted = false", targetID).
			Update("is_hidden", hidden)
		if result.Error != nil || result.RowsAffected != 1 {
			if result.Error != nil {
				return result.Error
			}
			return gorm.ErrRecordNotFound
		}
		return nil
	default:
		return errors.New("target_type non valido")
	}
}

func (r *Repository) ListActions(cityIDs []string, limit int) ([]models.ModerationAction, error) {
	if limit <= 0 || limit > 200 {
		limit = 100
	}
	if len(cityIDs) == 0 {
		return []models.ModerationAction{}, nil
	}
	var result []models.ModerationAction
	err := r.db.Table("moderation_actions AS ma").
		Select("DISTINCT ma.*").
		Where(`
			(ma.target_type = 'post' AND EXISTS (
				SELECT 1 FROM posts p WHERE p.id = ma.target_id AND p.city_id IN ?
			)) OR
			(ma.target_type = 'comment' AND EXISTS (
				SELECT 1 FROM comments c JOIN posts p ON p.id = c.post_id
				WHERE c.id = ma.target_id AND p.city_id IN ?
			)) OR
			(ma.target_type = 'user' AND EXISTS (
				SELECT 1 FROM users u JOIN universities uni ON uni.id = u.university_id
				WHERE u.id = ma.target_id AND uni.city_id IN ?
			)) OR EXISTS (
				SELECT 1 FROM reports r
				LEFT JOIN posts rp ON r.target_type = 'post' AND rp.id = r.target_id
				LEFT JOIN comments rc ON r.target_type = 'comment' AND rc.id = r.target_id
				LEFT JOIN posts rcp ON rcp.id = rc.post_id
				WHERE r.moderation_action_id = ma.id
				  AND COALESCE(rp.city_id, rcp.city_id) IN ?
			)
		`, cityIDs, cityIDs, cityIDs, cityIDs).
		Order("ma.created_at DESC").
		Limit(limit).
		Find(&result).Error
	return result, err
}
