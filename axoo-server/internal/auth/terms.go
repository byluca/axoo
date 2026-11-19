package auth

import (
	"axoo-server/internal/models"
	"errors"
	"gorm.io/gorm"
	"time"
)

const TermsVersion = "2026-10-07"

var ErrTerms = errors.New("Conferma di avere almeno 18 anni e accetta i Termini d’uso aggiornati.")

func (r *Repository) AcceptTerms(userID, version string, adult bool) error {
	if version != TermsVersion || !adult {
		return ErrTerms
	}
	result := r.db.Model(&models.User{}).Where("id = ?", userID).
		Updates(map[string]any{"terms_version": version, "terms_accepted_at": gorm.Expr("CASE WHEN terms_version = ? AND terms_accepted_at IS NOT NULL THEN terms_accepted_at ELSE ? END", version, time.Now().UTC())})
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected != 1 {
		return gorm.ErrRecordNotFound
	}
	return nil
}
func (s *Service) AcceptTerms(userID, version string, adult bool) error {
	return s.repo.AcceptTerms(userID, version, adult)
}
