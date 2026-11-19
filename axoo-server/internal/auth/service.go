package auth

import (
	"axoo-server/internal/models"
	"axoo-server/internal/universities"
	"crypto/sha256"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgconn"
	"gorm.io/gorm"
)

const maxNicknameAllocationAttempts = 128

// Reserved domain for individually provisioned accounts with a personal password.
const passwordAdminDomain = "account.example"

// Config contiene le configurazioni necessarie per creare il profilo AXOO.
type Config struct {
	EmailSalt string
}

type Service struct {
	repo   AuthRepository
	config Config
}

const (
	AccountStatusActive    = "active"
	AccountStatusSuspended = "suspended"
	AccountStatusBanned    = "banned"
)

type ModerationNotice struct {
	ID         string    `json:"id"`
	TargetType string    `json:"target_type"`
	Reason     string    `json:"reason"`
	CreatedAt  time.Time `json:"created_at"`
}

type SessionBootstrap struct {
	TermsRequired    bool              `json:"terms_required"`
	TermsVersion     string            `json:"terms_version"`
	Status           string            `json:"status"`
	Reason           string            `json:"reason,omitempty"`
	SuspendedUntil   *time.Time        `json:"suspended_until,omitempty"`
	ModerationNotice *ModerationNotice `json:"moderation_notice,omitempty"`
	AndroidLocked    bool              `json:"android_locked"`
}

func NewService(repo AuthRepository, cfg Config) *Service {
	return &Service{repo: repo, config: cfg}
}

// hashEmail crea hash(email + salt) — l'email non viene mai salvata.
func (s *Service) hashEmail(email string) string {
	h := sha256.Sum256([]byte(email + s.config.EmailSalt))
	return fmt.Sprintf("%x", h)
}

// EnsureUser crea soltanto il profilo applicativo dopo che Supabase ha gia' verificato l'OTP.
func (s *Service) EnsureUser(supabaseUserID, rawEmail string) error {
	email, university, isAlphaTester, err := s.resolveStudentEmail(rawEmail)
	if err != nil {
		return err
	}
	emailHash := s.hashEmail(email)

	_, err = s.repo.FindUserByID(supabaseUserID)
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return s.createUserWithNickname(
			&models.User{
				ID:            supabaseUserID,
				UniversityID:  university.ID,
				EmailHash:     emailHash,
				IsAlphaTester: isAlphaTester,
			},
		)
	} else if err != nil {
		return err
	}
	return nil
}

func (s *Service) BootstrapSession(supabaseUserID, rawEmail string) (*SessionBootstrap, error) {
	if err := s.EnsureUser(supabaseUserID, rawEmail); err != nil {
		return nil, err
	}
	user, err := s.repo.FindUserByID(supabaseUserID)
	if err != nil {
		return nil, err
	}
	if user.IsBanned {
		return &SessionBootstrap{
			Status: AccountStatusBanned,
			Reason: user.SuspensionReason,
		}, nil
	}
	if user.SuspendedUntil != nil && user.SuspendedUntil.After(time.Now().UTC()) {
		return &SessionBootstrap{
			Status:         AccountStatusSuspended,
			Reason:         user.SuspensionReason,
			SuspendedUntil: user.SuspendedUntil,
		}, nil
	}

	// Keep android_locked in the response for installed clients; public access is open.
	bootstrap := &SessionBootstrap{Status: AccountStatusActive, AndroidLocked: false, TermsRequired: user.TermsVersion != TermsVersion || user.TermsAcceptedAt == nil, TermsVersion: TermsVersion}
	notice, err := s.repo.FindPendingModerationNotice(supabaseUserID)
	if err == nil {
		bootstrap.ModerationNotice = notice
	}
	return bootstrap, nil
}

func (s *Service) AcknowledgeModerationNotice(userID, noticeID string) error {
	if _, err := uuid.Parse(noticeID); err != nil {
		return gorm.ErrRecordNotFound
	}
	return s.repo.AcknowledgeModerationNotice(userID, noticeID)
}

// IsEmailEligible is the server-side authority used before asking Supabase to send an OTP.
func (s *Service) IsEmailEligible(rawEmail string) (bool, error) {
	_, _, _, err := s.resolveStudentEmail(rawEmail)
	if err == nil {
		return true, nil
	}
	if errors.Is(err, universities.ErrUnsupportedStudentEmail) {
		return false, nil
	}
	return false, err
}

func (s *Service) createUserWithNickname(user *models.User) error {
	for attempt := 0; attempt < maxNicknameAllocationAttempts; attempt++ {
		nickname, err := generateNickname()
		if err != nil {
			return err
		}
		user.Nickname = nickname
		err = s.repo.CreateUser(user)
		if err == nil {
			return nil
		}
		if !isNicknameConflict(err) {
			return err
		}
	}
	return errors.New("nickname pool temporarily unavailable")
}

func isNicknameConflict(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) &&
		pgErr.Code == "23505" &&
		strings.Contains(strings.ToLower(pgErr.ConstraintName), "nickname")
}

func (s *Service) resolveStudentEmail(rawEmail string) (string, *models.University, bool, error) {
	email, domain, err := universities.NormalizeStudentEmail(rawEmail)
	if err != nil {
		return "", nil, false, err
	}
	university, err := s.repo.FindUniversityByDomain(domain)
	isAlphaTester := false
	if errors.Is(err, gorm.ErrRecordNotFound) {
		if domain == passwordAdminDomain {
			admin, adminErr := s.repo.FindPasswordAdminByEmailHash(s.hashEmail(email))
			if adminErr == nil {
				return email, &models.University{ID: admin.UniversityID}, false, nil
			}
			if !errors.Is(adminErr, gorm.ErrRecordNotFound) {
				return "", nil, false, adminErr
			}
		}
		official, officialErr := s.repo.FindOfficialUserByEmailHash(s.hashEmail(email))
		if officialErr == nil {
			return email, &models.University{ID: official.UniversityID}, false, nil
		}
		if !errors.Is(officialErr, gorm.ErrRecordNotFound) {
			return "", nil, false, officialErr
		}
		university, err = s.repo.FindAlphaTesterUniversity(s.hashEmail(email))
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return "", nil, false, universities.ErrUnsupportedStudentEmail
		}
		isAlphaTester = err == nil
	}
	if err != nil {
		return "", nil, false, err
	}
	return email, university, isAlphaTester, nil
}

// Managed accounts use their own Supabase password, never the shared alpha password.
func (s *Service) IsPasswordLoginEmail(rawEmail string) (bool, error) {
	email, domain, err := universities.NormalizeStudentEmail(rawEmail)
	if err != nil {
		return false, nil
	}
	if domain == passwordAdminDomain {
		_, adminErr := s.repo.FindPasswordAdminByEmailHash(s.hashEmail(email))
		if adminErr == nil {
			return true, nil
		}
		if !errors.Is(adminErr, gorm.ErrRecordNotFound) {
			return false, adminErr
		}
	}
	user, err := s.repo.FindOfficialUserByEmailHash(s.hashEmail(email))
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return user.AvatarConfig["official_account"] == "aup", nil
}
