package events

import (
	"axoo-server/internal/media"
	"errors"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
	"time"
)

type Repository struct{ db *gorm.DB }

func NewRepository(db *gorm.DB) *Repository { return &Repository{db: db} }

func (r *Repository) Audience(userID string) (Audience, error) {
	var audience Audience
	err := r.db.Table("users u").Select("uni.city_id, u.is_admin, u.nickname, u.avatar_config").
		Joins("JOIN universities uni ON uni.id = u.university_id").
		Where("u.id = ?", userID).Take(&audience).Error
	return audience, err
}
func (r *Repository) List(cityID string, today time.Time, cursor *Cursor, limit int) ([]Event, error) {
	query := r.visible(cityID).Where("e.starts_at >= ?", today)
	if cursor != nil {
		query = query.Where("(e.starts_at, e.id) > (?, ?)", cursor.StartsAt, cursor.ID)
	}
	items := []Event{}
	err := query.Order("e.starts_at ASC, e.id ASC").Limit(limit).Find(&items).Error
	return items, err
}
func (r *Repository) Create(userID string, event *Event) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		if err := media.ValidatePostPhoto(tx, userID, event.Poster); err != nil {
			return err
		}
		return tx.Create(event).Error
	})
}
func (r *Repository) Delete(cityID, id string) error {
	result := r.db.Where("city_id = ? AND id = ?", cityID, id).Delete(&Event{})
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected == 0 {
		return ErrNotFound
	}
	return nil
}

func (r *Repository) visible(cityID string) *gorm.DB {
	return r.db.Table("campus_events e").Select("e.*, COALESCE(p.nickname, '') AS organizer_name, p.avatar_config AS organizer_avatar_config").
		Joins("LEFT JOIN users p ON p.id=e.organizer_id").Where("e.city_id = ?", cityID)
}
func (r *Repository) Get(cityID, id string) (*Event, error) {
	var event Event
	err := r.visible(cityID).Where("e.id = ?", id).Take(&event).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, ErrNotFound
	}
	return &event, err
}
func (r *Repository) Update(userID, cityID, id string, input Input) error {
	return r.db.Transaction(func(tx *gorm.DB) error {
		var existing Event
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Where("city_id = ? AND id = ?", cityID, id).Take(&existing).Error; err != nil {
			if errors.Is(err, gorm.ErrRecordNotFound) {
				return ErrNotFound
			}
			return err
		}
		if input.Poster != nil && (existing.Poster == nil || input.Poster.URL != existing.Poster.URL) {
			if err := media.ValidatePostPhoto(tx, userID, input.Poster); err != nil {
				return err
			}
		}
		return tx.Model(&Event{}).Where("id = ?", id).Updates(map[string]any{
			"title": input.Title, "description": input.Description, "location": input.Location, "poster": input.Poster,
			"starts_at": input.StartsAt.UTC(), "ends_at": input.EndsAt, "address": input.Address, "entry_info": input.EntryInfo,
		}).Error
	})
}
