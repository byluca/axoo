package users

import (
	"axoo-server/internal/models"
	"errors"
	"sort"
	"strings"
	"unicode"
	"unicode/utf8"

	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
	"gorm.io/gorm"
)

const MaxBioLength = 200

var ErrInvalidBio = errors.New("la bio può avere massimo 200 caratteri")
var ErrInvalidBioEntities = errors.New("emoji della bio non valide")

// PublicProfile deliberately contains only information shown to other students.
type PublicProfile struct {
	IsProfilePublic bool                      `json:"is_profile_public"`
	ID              string                    `json:"id"`
	Nickname        string                    `json:"nickname"`
	Bio             string                    `json:"bio"`
	BioEntities     models.MessageEntitySlice `json:"bio_entities" gorm:"type:jsonb"`
	UniversityID    string                    `json:"university_id"`
	UniversityName  string                    `json:"university_name"`
	AvatarConfig    models.AvatarConfig       `json:"avatar_config" gorm:"type:jsonb"`
}

func normalizeBio(raw string) (string, error) {
	bio := strings.TrimSpace(raw)
	if !utf8.ValidString(bio) || utf8.RuneCountInString(bio) > MaxBioLength || strings.ContainsRune(bio, '\x00') {
		return "", ErrInvalidBio
	}
	return bio, nil
}

func normalizeBioEntities(raw, bio string, entities []models.MessageEntity) (models.MessageEntitySlice, error) {
	if len(entities) > 20 {
		return nil, ErrInvalidBioEntities
	}
	leading := utf8.RuneCountInString(raw) - utf8.RuneCountInString(strings.TrimLeftFunc(raw, unicode.IsSpace))
	length := utf8.RuneCountInString(bio)
	result := append(models.MessageEntitySlice{}, entities...)
	sort.SliceStable(result, func(i, j int) bool { return result[i].Offset < result[j].Offset })
	boundary := 0
	for i, entity := range result {
		if (entity.Type != "custom_emoji" && entity.Type != "static_emoji") || entity.UserID != "" ||
			entity.CustomEmojiID < 0 || (entity.CustomEmojiID >= 63000 && entity.CustomEmojiID <= 63045) || entity.Offset < leading || entity.Length <= 0 ||
			entity.Offset-leading > length-entity.Length || entity.Offset-leading < boundary {
			return nil, ErrInvalidBioEntities
		}
		result[i].Offset -= leading
		boundary = result[i].Offset + entity.Length
	}
	return result, nil
}

func (s *Service) UpdateBio(userID, raw string, entities []models.MessageEntity) (*models.User, error) {
	bio, err := normalizeBio(raw)
	if err != nil {
		return nil, err
	}
	validated, err := normalizeBioEntities(raw, bio, entities)
	if err != nil {
		return nil, err
	}
	if err := s.repo.UpdateBio(userID, bio, validated); err != nil {
		return nil, err
	}
	return s.repo.FindByID(userID)
}

func (r *Repository) UpdateBio(userID, bio string, entities models.MessageEntitySlice) error {
	return r.db.Model(&models.User{}).Where("id = ?", userID).
		Updates(map[string]any{"bio": bio, "bio_entities": entities}).Error
}

func (s *Service) PublicProfile(viewerID, userID string) (*PublicProfile, error) {
	if _, err := uuid.Parse(userID); err != nil {
		return nil, gorm.ErrRecordNotFound
	}
	return s.repo.PublicProfile(viewerID, userID)
}

func (r *Repository) PublicProfile(viewerID, userID string) (*PublicProfile, error) {
	var profile PublicProfile
	err := r.db.Table("users AS u").
		Select("u.id, u.nickname, u.bio, u.bio_entities, u.university_id, uni.name AS university_name, u.avatar_config, u.is_profile_public").
		Joins("JOIN universities uni ON uni.id = u.university_id").
		Joins("JOIN users viewer ON viewer.id = ?", viewerID).
		Joins("JOIN universities viewer_uni ON viewer_uni.id = viewer.university_id AND viewer_uni.city_id = uni.city_id").
		Where("u.id = ? AND u.is_banned = false", userID).
		Where("u.suspended_until IS NULL OR u.suspended_until <= NOW()").
		Where("NOT EXISTS (SELECT 1 FROM user_blocks b WHERE b.blocker_id = ? AND b.blocked_id = u.id)", viewerID).
		Take(&profile).Error
	return &profile, err
}

func (h *Handler) getPublicProfile(c *fiber.Ctx) error {
	profile, err := h.service.PublicProfile(c.Locals("user_id").(string), c.Params("userId"))
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return c.Status(404).JSON(fiber.Map{"error": "profilo non disponibile"})
	}
	if err != nil {
		return c.Status(500).JSON(fiber.Map{"error": "errore caricamento profilo"})
	}
	return c.JSON(profile)
}

func (h *Handler) updateBio(c *fiber.Ctx) error {
	var body struct {
		Bio         *string                `json:"bio"`
		BioEntities []models.MessageEntity `json:"bio_entities"`
	}
	if err := c.BodyParser(&body); err != nil || body.Bio == nil {
		return c.Status(400).JSON(fiber.Map{"error": "bio mancante"})
	}
	user, err := h.service.UpdateBio(c.Locals("user_id").(string), *body.Bio, body.BioEntities)
	if errors.Is(err, ErrInvalidBio) || errors.Is(err, ErrInvalidBioEntities) {
		return c.Status(400).JSON(fiber.Map{"error": err.Error()})
	}
	if err != nil {
		return c.Status(500).JSON(fiber.Map{"error": "errore aggiornamento bio"})
	}
	return c.JSON(profileResponse(user))
}
