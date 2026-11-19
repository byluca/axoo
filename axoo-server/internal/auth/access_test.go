package auth

import (
	"axoo-server/internal/models"
	"gorm.io/gorm"
	"testing"
	"time"
)

type accessTestRepository struct {
	AuthRepository
	user models.User
}

func (r *accessTestRepository) FindUniversityByDomain(string) (*models.University, error) {
	return &models.University{}, nil
}
func (r *accessTestRepository) FindUserByID(string) (*models.User, error) {
	return &r.user, nil
}
func (r *accessTestRepository) FindPendingModerationNotice(string) (*ModerationNotice, error) {
	return nil, gorm.ErrRecordNotFound
}

func TestBootstrapFeedAccess(t *testing.T) {
	until := time.Now().Add(time.Hour)
	tests := []struct {
		name   string
		user   models.User
		status string
		locked bool
	}{
		{name: "university account", user: models.User{}, status: AccountStatusActive},
		{name: "alpha tester", user: models.User{IsAlphaTester: true}, status: AccountStatusActive},
		{name: "moderator", user: models.User{IsModerator: true}, status: AccountStatusActive},
		{name: "administrator", user: models.User{IsAdmin: true}, status: AccountStatusActive},
		{name: "banned alpha tester", user: models.User{IsAlphaTester: true, IsBanned: true}, status: AccountStatusBanned},
		{name: "suspended alpha tester", user: models.User{IsAlphaTester: true, SuspendedUntil: &until}, status: AccountStatusSuspended},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			service := NewService(&accessTestRepository{user: tt.user}, Config{})
			got, err := service.BootstrapSession("user", "student@studenti.uniba.it")
			if err != nil {
				t.Fatal(err)
			}
			if got.Status != tt.status || got.AndroidLocked != tt.locked {
				t.Fatalf("status=%s locked=%v; want status=%s locked=%v", got.Status, got.AndroidLocked, tt.status, tt.locked)
			}
		})
	}
}
