package auth

import (
	"errors"
	"testing"

	"axoo-server/internal/models"
	"gorm.io/gorm"
)

type passwordAdminRepository struct {
	officialTestRepository
	admin      *models.User
	err        error
	lookupHash string
}

func (r *passwordAdminRepository) FindPasswordAdminByEmailHash(hash string) (*models.User, error) {
	r.lookupHash = hash
	if r.err != nil {
		return nil, r.err
	}
	if r.admin == nil || !r.admin.IsAdmin || r.admin.IsAlphaTester {
		return nil, gorm.ErrRecordNotFound
	}
	return r.admin, nil
}

func TestDedicatedAdminPasswordLogin(t *testing.T) {
	for _, tc := range []struct {
		name, email string
		user        *models.User
		password    bool
	}{
		{"dedicated admin", " Eventi@Account.Example ", &models.User{UniversityID: "poliba", IsAdmin: true}, true},
		{"unregistered", "eventi@account.example", nil, false},
		{"ordinary account", "eventi@account.example", &models.User{UniversityID: "poliba"}, false},
		{"shared alpha account", "eventi@account.example", &models.User{UniversityID: "poliba", IsAdmin: true, IsAlphaTester: true}, false},
		{"university admin keeps OTP", "eventi@studenti.poliba.it", &models.User{UniversityID: "poliba", IsAdmin: true}, false},
		{"different domain", "eventi@account.example.invalid", &models.User{UniversityID: "poliba", IsAdmin: true}, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			repo := &passwordAdminRepository{admin: tc.user}
			service := NewService(repo, Config{EmailSalt: "test-salt"})
			enabled, err := service.IsPasswordLoginEmail(tc.email)
			if err != nil || enabled != tc.password {
				t.Fatalf("password=%v err=%v", enabled, err)
			}
			if tc.password {
				if repo.lookupHash != service.hashEmail("eventi@account.example") {
					t.Fatal("unnormalized password lookup")
				}
				email, university, alpha, err := service.resolveStudentEmail(tc.email)
				if err != nil || email != "eventi@account.example" || university.ID != "poliba" || alpha {
					t.Fatalf("wrong managed account audience: %v", err)
				}
			}
		})
	}
}

func TestDedicatedAdminRevocationDisablesLogin(t *testing.T) {
	repo := &passwordAdminRepository{admin: &models.User{UniversityID: "poliba", IsAdmin: true}}
	service := NewService(repo, Config{EmailSalt: "test-salt"})
	if enabled, err := service.IsPasswordLoginEmail("eventi@account.example"); err != nil || !enabled {
		t.Fatal("dedicated admin unavailable")
	}
	repo.admin.IsAdmin = false
	if enabled, err := service.IsPasswordLoginEmail("eventi@account.example"); err != nil || enabled {
		t.Fatal("revoked account still has password login")
	}
	if eligible, err := service.IsEmailEligible("eventi@account.example"); err != nil || eligible {
		t.Fatal("revoked account still eligible")
	}
}

func TestDedicatedAdminLookupFailureDoesNotEnableLogin(t *testing.T) {
	failure := errors.New("database unavailable")
	repo := &passwordAdminRepository{err: failure}
	service := NewService(repo, Config{EmailSalt: "test-salt"})
	if enabled, err := service.IsPasswordLoginEmail("eventi@account.example"); enabled || !errors.Is(err, failure) {
		t.Fatal("lookup failure must not enable login")
	}
	if eligible, err := service.IsEmailEligible("eventi@account.example"); eligible || !errors.Is(err, failure) {
		t.Fatal("lookup failure must not enable eligibility")
	}
}
