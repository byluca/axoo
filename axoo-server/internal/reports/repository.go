package reports

import (
	"axoo-server/internal/models"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

type Repository struct {
	db *gorm.DB
}

func NewRepository(db *gorm.DB) *Repository {
	return &Repository{db: db}
}

func (r *Repository) Create(report *models.Report) (bool, error) {
	result := r.db.Clauses(clause.OnConflict{
		Columns: []clause.Column{
			{Name: "reporter_id"},
			{Name: "target_type"},
			{Name: "target_id"},
		},
		DoNothing: true,
	}).Create(report)
	return result.RowsAffected == 1, result.Error
}

func (r *Repository) TargetExists(targetType, targetID string) bool {
	var count int64
	switch targetType {
	case "user":
		r.db.Model(&models.User{}).Where("id = ?", targetID).Count(&count)
	case "post":
		r.db.Model(&models.Post{}).Where("id = ? AND is_deleted = false", targetID).Count(&count)
	case "comment":
		r.db.Model(&models.Comment{}).Where("id = ? AND is_deleted = false", targetID).Count(&count)
	}
	return count > 0
}
