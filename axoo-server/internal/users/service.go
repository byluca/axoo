package users

import (
	"axoo-server/internal/models"
	"errors"
	"log"
	"strings"

	"github.com/jackc/pgx/v5/pgconn"
)

var (
	ErrOfficialAvatar      = errors.New("avatar degli account ufficiali gestito da Axoo")
	ErrInvalidNickname     = errors.New("usa da 3 a 24 caratteri: lettere minuscole e numeri")
	ErrNicknameUnavailable = errors.New("nickname non disponibile")
)

type Service struct {
	repo   *Repository
	config Config
}

type Config struct {
	AuthAccountDeleter AuthAccountDeleter
}

type AuthAccountDeleter interface {
	DeleteUser(userID string) error
}

func NewService(repo *Repository, configs ...Config) *Service {
	service := &Service{repo: repo}
	if len(configs) > 0 {
		service.config = configs[0]
	}
	return service
}

func (s *Service) GetProfile(userID string) (*models.User, error) {
	return s.repo.FindByID(userID)
}

func (s *Service) UpdateAvatarConfig(userID string, config models.AvatarConfig) error {
	if _, reserved := config["official_account"]; reserved {
		return ErrOfficialAvatar
	}
	return s.repo.UpdateAvatarConfig(userID, config)
}

func (s *Service) NicknameAvailable(userID, rawNickname string) (bool, error) {
	if name := strings.ToLower(strings.TrimSpace(rawNickname)); name == "axoo" || name == "aup" {
		return false, nil
	}
	nickname, err := normalizeNickname(rawNickname)
	if err != nil {
		return false, err
	}
	return s.repo.NicknameAvailable(userID, nickname)
}

func (s *Service) UpdateNickname(userID, rawNickname string) (*models.User, error) {
	if name := strings.ToLower(strings.TrimSpace(rawNickname)); name == "axoo" || name == "aup" {
		return nil, ErrNicknameUnavailable
	}
	nickname, err := normalizeNickname(rawNickname)
	if err != nil {
		return nil, err
	}
	if err := s.repo.UpdateNickname(userID, nickname); err != nil {
		var pgErr *pgconn.PgError
		if errors.As(err, &pgErr) && pgErr.Code == "23505" {
			return nil, ErrNicknameUnavailable
		}
		return nil, err
	}
	return s.repo.FindByID(userID)
}

func normalizeNickname(raw string) (string, error) {
	nickname := strings.ToLower(strings.TrimSpace(raw))
	if len(nickname) < 3 || len(nickname) > 24 {
		return "", ErrInvalidNickname
	}
	for _, character := range nickname {
		if (character < 'a' || character > 'z') && (character < '0' || character > '9') {
			return "", ErrInvalidNickname
		}
	}
	return nickname, nil
}

func (s *Service) BlockUser(blockerID, blockedID string) error {
	if _, err := s.repo.FindByID(blockedID); err != nil {
		return err
	}
	return s.repo.BlockUser(blockerID, blockedID)
}

func (s *Service) UnblockUser(blockerID, blockedID string) error {
	return s.repo.UnblockUser(blockerID, blockedID)
}

func (s *Service) BlockedUsers(userID string) ([]models.User, error) {
	return s.repo.BlockedUsers(userID)
}

func (s *Service) DeleteAccount(userID string) error {
	if s.config.AuthAccountDeleter == nil {
		return errors.New("auth account deleter non configurato")
	}
	if err := s.repo.DeleteAccount(userID); err != nil {
		return err
	}
	// The worker completes provider deletion from the durable outbox. Do not keep
	// the phone request waiting on Supabase after application data was removed.
	return nil
}

func (s *Service) completeAuthDeletion(userID string) error {
	if err := s.config.AuthAccountDeleter.DeleteUser(userID); err != nil {
		return err
	}
	return s.repo.CompleteAccountDeletion(userID)
}

func (s *Service) RetryAccountDeletions() error {
	if s.config.AuthAccountDeleter == nil {
		return errors.New("auth account deleter non configurato")
	}
	ids, err := s.repo.PendingAccountDeletions()
	if err != nil {
		return err
	}
	for _, id := range ids {
		if err := s.completeAuthDeletion(id); err != nil {
			log.Printf("account deletion retry failed: %v", err)
		}
	}
	return nil
}
