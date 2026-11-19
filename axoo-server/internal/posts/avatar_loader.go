package posts

import (
	"axoo-server/internal/models"

	"gorm.io/gorm"
)

// DBAvatarConfigLoader implementa AvatarConfigLoader leggendo dal database.
type DBAvatarConfigLoader struct {
	db *gorm.DB
}

// costruttore in Golang
func NewDBAvatarConfigLoader(db *gorm.DB) *DBAvatarConfigLoader {
	return &DBAvatarConfigLoader{db: db}
}

func (r *DBAvatarConfigLoader) LoadAvatarConfigs(userIDs []string) (map[string]models.AvatarConfig, error) {
	if len(userIDs) == 0 {
		return nil, nil
	}
	var users []models.User
	if err := r.db.Select("id, avatar_config").Where("id IN ?", userIDs).Find(&users).Error; err != nil {
		return nil, err
	}
	configMap := make(map[string]models.AvatarConfig, len(users))
	for _, u := range users {
		if u.AvatarConfig != nil {
			configMap[u.ID] = u.AvatarConfig
		}
	}
	return configMap, nil
}
