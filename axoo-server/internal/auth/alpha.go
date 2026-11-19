package auth

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"axoo-server/internal/models"
	"axoo-server/internal/universities"
	"golang.org/x/crypto/bcrypt"
	"gorm.io/gorm"
)

var ErrAlphaCredentials = errors.New("email o password non validi")

type AlphaSession struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	ExpiresIn    int    `json:"expires_in"`
	TokenType    string `json:"token_type"`
	User         struct {
		ID    string `json:"id"`
		Email string `json:"email"`
	} `json:"user"`
}
type alphaStore interface {
	PasswordHash(context.Context) ([]byte, error)
	Allowed(context.Context, string) (bool, error)
	MarkAlpha(context.Context, string) error
	AccountAllowed(context.Context, string) (bool, error)
}
type alphaProvider interface {
	Session(context.Context, string) (*AlphaSession, error)
}
type AlphaLogin struct {
	store      alphaStore
	provider   alphaProvider
	salt       string
	ensureUser func(string, string) error
}

func NewAlphaLogin(db *gorm.DB, service *Service, supabaseURL, key, salt string) *AlphaLogin {
	return &AlphaLogin{store: dbAlphaStore{db}, provider: supabaseAlphaProvider{strings.TrimRight(supabaseURL, "/"), key, &http.Client{Timeout: 15 * time.Second}}, salt: salt, ensureUser: service.EnsureUser}
}
func (a *AlphaLogin) email(raw string) (string, string, error) {
	email, _, err := universities.NormalizeStudentEmail(raw)
	if err != nil || len(email) > 254 {
		return "", "", ErrAlphaCredentials
	}
	hash := sha256.Sum256([]byte(email + a.salt))
	return email, fmt.Sprintf("%x", hash), nil
}
func (a *AlphaLogin) Enabled(ctx context.Context, raw string) (bool, error) {
	_, hash, err := a.email(raw)
	if err != nil {
		return false, nil
	}
	allowed, err := a.store.Allowed(ctx, hash)
	if err != nil || !allowed {
		return false, err
	}
	password, err := a.store.PasswordHash(ctx)
	return len(password) > 0, err
}
func (a *AlphaLogin) Login(ctx context.Context, raw, password string) (*AlphaSession, error) {
	email, hash, err := a.email(raw)
	if err != nil || password == "" || len(password) > 128 {
		return nil, ErrAlphaCredentials
	}
	stored, err := a.store.PasswordHash(ctx)
	if err != nil {
		return nil, err
	}
	if len(stored) == 0 || bcrypt.CompareHashAndPassword(stored, []byte(password)) != nil {
		return nil, ErrAlphaCredentials
	}
	allowed, err := a.store.Allowed(ctx, hash)
	if err != nil {
		return nil, err
	}
	if !allowed {
		return nil, ErrAlphaCredentials
	}
	session, err := a.provider.Session(ctx, email)
	if err != nil {
		return nil, err
	}
	if session == nil || session.AccessToken == "" || session.RefreshToken == "" || session.ExpiresIn <= 0 || session.User.ID == "" || strings.ToLower(session.User.Email) != email {
		return nil, errors.New("sessione alpha non valida")
	}
	accountAllowed, err := a.store.AccountAllowed(ctx, session.User.ID)
	if err != nil {
		return nil, err
	}
	if !accountAllowed {
		return nil, ErrAlphaCredentials
	}
	if err = a.ensureUser(session.User.ID, email); err != nil {
		return nil, err
	}
	// The existing active-user middleware also enforces later expiry/revocation.
	if err = a.store.MarkAlpha(ctx, session.User.ID); err != nil {
		return nil, err
	}
	allowed, err = a.store.Allowed(ctx, hash)
	if err != nil {
		return nil, err
	}
	if !allowed {
		return nil, ErrAlphaCredentials
	}
	return session, nil
}

type dbAlphaStore struct{ db *gorm.DB }

func (s dbAlphaStore) PasswordHash(ctx context.Context) ([]byte, error) {
	var hash string
	err := s.db.WithContext(ctx).Table("alpha_login_settings").Select("password_hash").Where("id=1 AND enabled=TRUE").Scan(&hash).Error
	return []byte(hash), err
}
func (s dbAlphaStore) Allowed(ctx context.Context, hash string) (bool, error) {
	var count int64
	err := s.db.WithContext(ctx).Table("alpha_testers").Where("email_hash=? AND active=TRUE AND (expires_at IS NULL OR expires_at>NOW())", hash).
		Where(`(NOT EXISTS (SELECT 1 FROM users u WHERE u.email_hash=alpha_testers.email_hash AND (u.is_admin OR u.is_moderator))
		OR EXISTS (SELECT 1 FROM alpha_login_settings WHERE id=1 AND allow_privileged=TRUE))`).Count(&count).Error
	return count == 1, err
}
func (s dbAlphaStore) AccountAllowed(ctx context.Context, id string) (bool, error) {
	var count int64
	if err := s.db.WithContext(ctx).Model(&models.User{}).Where("id=? AND (is_admin OR is_moderator)", id).Count(&count).Error; err != nil {
		return false, err
	}
	if count == 0 {
		return true, nil
	}
	var allowed bool
	err := s.db.WithContext(ctx).Table("alpha_login_settings").Select("allow_privileged").Where("id=1 AND enabled=TRUE").Scan(&allowed).Error
	return allowed, err
}
func (s dbAlphaStore) MarkAlpha(ctx context.Context, id string) error {
	result := s.db.WithContext(ctx).Model(&models.User{}).Where("id=?", id).Update("is_alpha_tester", true)
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected != 1 {
		return errors.New("profilo alpha non disponibile")
	}
	return nil
}

type supabaseAlphaProvider struct {
	base, key string
	client    *http.Client
}

func (s supabaseAlphaProvider) request(ctx context.Context, path string, input, output any) error {
	raw, err := json.Marshal(input)
	if err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, "POST", s.base+"/auth/v1/"+path, bytes.NewReader(raw))
	if err != nil {
		return errors.New("configurazione autenticazione non valida")
	}
	req.Header.Set("apikey", s.key)
	req.Header.Set("Authorization", "Bearer "+s.key)
	req.Header.Set("Content-Type", "application/json")
	res, err := s.client.Do(req)
	if err != nil {
		return errors.New("servizio autenticazione non raggiungibile")
	}
	defer res.Body.Close()
	if res.StatusCode != 200 {
		return fmt.Errorf("servizio autenticazione: HTTP %d", res.StatusCode)
	}
	if json.NewDecoder(io.LimitReader(res.Body, 128*1024)).Decode(output) != nil {
		return errors.New("risposta autenticazione non valida")
	}
	return nil
}
func (s supabaseAlphaProvider) Session(ctx context.Context, email string) (*AlphaSession, error) {
	// Generate and exchange the provider's one-use token entirely on the server;
	// no email is sent and the app receives standard refreshable Supabase tokens.
	var link struct {
		Hash string `json:"hashed_token"`
	}
	if err := s.request(ctx, "admin/generate_link", map[string]string{"type": "magiclink", "email": email}, &link); err != nil {
		return nil, err
	}
	if link.Hash == "" {
		return nil, errors.New("token alpha non disponibile")
	}
	var session AlphaSession
	if err := s.request(ctx, "verify", map[string]string{"type": "email", "token_hash": link.Hash}, &session); err != nil {
		return nil, err
	}
	return &session, nil
}
