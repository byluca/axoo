package auth

import (
	"axoo-server/internal/models"
	"gorm.io/gorm"
	"testing"
)

type officialTestRepository struct {
	AuthRepository
	official *models.User
	hash     string
}

func (r *officialTestRepository) FindUniversityByDomain(string) (*models.University, error) {
	return nil, gorm.ErrRecordNotFound
}
func (r *officialTestRepository) FindOfficialUserByEmailHash(hash string) (*models.User, error) {
	r.hash = hash
	if r.official == nil {
		return nil, gorm.ErrRecordNotFound
	}
	return r.official, nil
}
func (r *officialTestRepository) FindAlphaTesterUniversity(string) (*models.University, error) {
	return nil, gorm.ErrRecordNotFound
}
func (r *officialTestRepository) FindPasswordAdminByEmailHash(string) (*models.User, error) {
	return nil, gorm.ErrRecordNotFound
}

func TestOfficialPasswordEligibilityAndCampus(t *testing.T) {
	for _, kind := range []string{"aup", "axoo", ""} {
		t.Run(kind, func(t *testing.T) {
			repo := &officialTestRepository{}
			if kind != "" {
				repo.official = &models.User{UniversityID: "poliba", AvatarConfig: models.AvatarConfig{"official_account": kind}}
			}
			service := NewService(repo, Config{EmailSalt: "test-salt"})
			enabled, err := service.IsPasswordLoginEmail(" AUP@account.example ")
			if err != nil || enabled != (kind == "aup") {
				t.Fatalf("enabled=%v err=%v", enabled, err)
			}
			if repo.hash != service.hashEmail("aup@account.example") {
				t.Fatal("email must use the normalized salted hash")
			}
			eligible, err := service.IsEmailEligible("aup@account.example")
			if err != nil || eligible != (kind != "") {
				t.Fatalf("eligible=%v err=%v", eligible, err)
			}
			if kind != "" {
				_, uni, alpha, err := service.resolveStudentEmail("aup@account.example")
				if err != nil || uni.ID != "poliba" || alpha {
					t.Fatal("official campus must be preserved without alpha privileges")
				}
			}
		})
	}
}
