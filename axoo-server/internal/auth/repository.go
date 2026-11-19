package auth

import (
	"axoo-server/internal/models"
	"errors"
	"time"

	"gorm.io/gorm"
)

// AuthRepository definisce le operazioni di persistenza per l'autenticazione.
type AuthRepository interface {
	AcceptTerms(userID, version string, adult bool) error
	FindUserByID(id string) (*models.User, error)
	CreateUser(user *models.User) error
	FindUniversityByDomain(domain string) (*models.University, error)
	FindAlphaTesterUniversity(emailHash string) (*models.University, error)
	FindOfficialUserByEmailHash(emailHash string) (*models.User, error)
	FindPasswordAdminByEmailHash(emailHash string) (*models.User, error)
	FindPendingModerationNotice(userID string) (*ModerationNotice, error)
	AcknowledgeModerationNotice(userID, noticeID string) error
}

type Repository struct {
	db *gorm.DB
}

var ErrAccountDeleted = errors.New("account eliminato")

func NewRepository(db *gorm.DB) *Repository {
	return &Repository{db: db}
}

func (r *Repository) FindUserByID(id string) (*models.User, error) {
	var deleted int64
	if err := r.db.Table("account_deletions").Where("user_id = ?", id).Count(&deleted).Error; err != nil {
		return nil, err
	}
	if deleted > 0 {
		return nil, ErrAccountDeleted
	}
	var user models.User
	err := r.db.First(&user, "id = ?", id).Error
	if err != nil {
		return nil, err
	}
	return &user, nil
}

func (r *Repository) CreateUser(user *models.User) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.Exec("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", "account:"+user.ID).Error; err != nil {
			return err
		}
		var deleted int64
		if err := tx.Table("account_deletions").Where("user_id = ?", user.ID).Count(&deleted).Error; err != nil {
			return err
		}
		if deleted > 0 {
			return ErrAccountDeleted
		}
		return tx.Create(user).Error
	})
}

func (r *Repository) FindUniversityByDomain(domain string) (*models.University, error) {
	var university models.University
	err := r.db.Where("LOWER(domain) = ?", domain).First(&university).Error
	if err != nil {
		return nil, err
	}
	return &university, nil
}

func (r *Repository) FindAlphaTesterUniversity(emailHash string) (*models.University, error) {
	var university models.University
	err := r.db.Table("universities").
		Select("universities.*").
		Joins("JOIN alpha_testers ON alpha_testers.university_id = universities.id").
		Where("alpha_testers.email_hash = ?", emailHash).
		Where("alpha_testers.active = TRUE").
		Where("alpha_testers.expires_at IS NULL OR alpha_testers.expires_at > NOW()").
		First(&university).Error
	if err != nil {
		return nil, err
	}
	return &university, nil
}

func (r *Repository) FindPendingModerationNotice(userID string) (*ModerationNotice, error) {
	var notice ModerationNotice
	err := r.db.Table("moderation_actions AS ma").
		Select("ma.id, ma.target_type, ma.reason, ma.created_at").
		Where("ma.action = 'hide' AND ma.author_seen_at IS NULL").
		Where(`
			(ma.target_type = 'post' AND EXISTS (
				SELECT 1 FROM posts p
				WHERE p.id = ma.target_id AND p.user_id = ? AND p.is_hidden = TRUE
			)) OR
			(ma.target_type = 'comment' AND EXISTS (
				SELECT 1 FROM comments c
				WHERE c.id = ma.target_id AND c.user_id = ? AND c.is_hidden = TRUE
			))
		`, userID, userID).
		Order("ma.created_at DESC").
		Take(&notice).Error
	if err != nil {
		return nil, err
	}
	return &notice, nil
}

func (r *Repository) AcknowledgeModerationNotice(userID, noticeID string) error {
	result := r.db.Model(&models.ModerationAction{}).
		Where("id = ? AND action = 'hide'", noticeID).
		Where(`
			(target_type = 'post' AND EXISTS (
				SELECT 1 FROM posts p
				WHERE p.id = moderation_actions.target_id AND p.user_id = ?
			)) OR
			(target_type = 'comment' AND EXISTS (
				SELECT 1 FROM comments c
				WHERE c.id = moderation_actions.target_id AND c.user_id = ?
			))
		`, userID, userID).
		Update("author_seen_at", time.Now().UTC())
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected != 1 {
		return gorm.ErrRecordNotFound
	}
	return nil
}

// Only server-managed metadata can enable an official account.
func (r *Repository) FindOfficialUserByEmailHash(emailHash string) (*models.User, error) {
	var user models.User
	err := r.db.Where("email_hash = ? AND avatar_config->>'official_account' IN ('axoo', 'aup')", emailHash).First(&user).Error
	return &user, err
}

// Dedicated staff identities are provisioned by an operator, never by signup.
func (r *Repository) FindPasswordAdminByEmailHash(emailHash string) (*models.User, error) {
	var user models.User
	err := r.db.Where("email_hash = ? AND is_admin = TRUE AND is_alpha_tester = FALSE", emailHash).First(&user).Error
	return &user, err
}
