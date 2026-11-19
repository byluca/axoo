package push

import (
	"axoo-server/internal/notifications"
	"context"
	"time"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

type Installation struct {
	InstallationID string `gorm:"primaryKey;type:uuid"`
	UserID         string `gorm:"type:uuid"`
	Registration   string
	UpdatedAt      time.Time
}

func (Installation) TableName() string { return "push_installations" }

type Repository struct{ db *gorm.DB }

func NewRepository(db *gorm.DB) *Repository { return &Repository{db: db} }

func (r *Repository) Register(ctx context.Context, userID, installationID, registration string) error {
	return r.db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		// Coordinate with account deletion and concurrent uploads of the same registration.
		for _, key := range []string{"account:" + userID, "push:" + registration} {
			if err := tx.Exec("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", key).Error; err != nil {
				return err
			}
		}
		if err := tx.Where("registration = ? AND installation_id <> ?", registration, installationID).
			Delete(&Installation{}).Error; err != nil {
			return err
		}
		return tx.Clauses(clause.OnConflict{
			Columns:   []clause.Column{{Name: "installation_id"}},
			DoUpdates: clause.AssignmentColumns([]string{"user_id", "registration", "updated_at"}),
		}).Create(&Installation{
			InstallationID: installationID, UserID: userID, Registration: registration,
		}).Error
	})
}

func (r *Repository) Remove(ctx context.Context, userID, installationID, registration string) error {
	// An old logout or FCM response must not remove a newer account or registration.
	return r.db.WithContext(ctx).
		Where("user_id = ? AND installation_id = ? AND registration = ?", userID, installationID, registration).
		Delete(&Installation{}).Error
}

func (r *Repository) ForComment(ctx context.Context, commentID string) ([]Installation, error) {
	var installations []Installation
	err := notifications.EligibleComments(r.db.WithContext(ctx).Table("comments c")).
		Joins("JOIN push_installations i ON i.user_id = u.id").Select("i.*").Where("c.id = ?", commentID).
		Find(&installations).Error
	return installations, err
}
