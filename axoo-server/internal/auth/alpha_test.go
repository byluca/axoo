package auth

import (
	"context"
	"crypto/sha256"
	"fmt"
	"golang.org/x/crypto/bcrypt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

type alphaTestStore struct {
	hash       []byte
	allowed    bool
	seen       string
	marked     bool
	privileged bool
}

func (s *alphaTestStore) AccountAllowed(context.Context, string) (bool, error) {
	return !s.privileged, nil
}

func TestAlphaPasswordDoesNotGrantUnapprovedAdministrativeAccess(t *testing.T) {
	hash, _ := bcrypt.GenerateFromPassword([]byte("test-only-password"), bcrypt.MinCost)
	store := &alphaTestStore{hash: hash, allowed: true, privileged: true}
	a := &AlphaLogin{store: store, provider: &alphaTestProvider{}, salt: "salt", ensureUser: func(string, string) error { t.Fatal("privileged account was provisioned"); return nil }}
	session, err := a.Login(context.Background(), "alpha@example.test", "test-only-password")
	if err != ErrAlphaCredentials || session != nil || store.marked {
		t.Fatal("administrative login was allowed")
	}
}

func (s *alphaTestStore) PasswordHash(context.Context) ([]byte, error) { return s.hash, nil }
func (s *alphaTestStore) Allowed(_ context.Context, hash string) (bool, error) {
	s.seen = hash
	return s.allowed, nil
}
func (s *alphaTestStore) MarkAlpha(context.Context, string) error { s.marked = true; return nil }

type alphaTestProvider struct {
	calls int
	email string
}

func (p *alphaTestProvider) Session(_ context.Context, email string) (*AlphaSession, error) {
	p.calls++
	p.email = email
	s := &AlphaSession{AccessToken: "access", RefreshToken: "refresh", ExpiresIn: 3600}
	s.User.ID = "user"
	s.User.Email = email
	return s, nil
}
func TestAlphaPasswordRequiresActiveInvitationAndNeverCallsProviderOnFailure(t *testing.T) {
	hash, _ := bcrypt.GenerateFromPassword([]byte("test-only-password"), bcrypt.MinCost)
	for _, test := range []struct {
		name, password   string
		allowed, success bool
	}{{"active", "test-only-password", true, true}, {"wrong password", "wrong", true, false}, {"revoked or expired", "test-only-password", false, false}, {"missing password", "", true, false}} {
		t.Run(test.name, func(t *testing.T) {
			store := &alphaTestStore{hash: hash, allowed: test.allowed}
			provider := &alphaTestProvider{}
			ensured := false
			a := &AlphaLogin{store: store, provider: provider, salt: "salt", ensureUser: func(id, email string) error { ensured = id == "user" && email == "alpha@example.test"; return nil }}
			session, err := a.Login(context.Background(), " Alpha@Example.Test ", test.password)
			if (err == nil) != test.success {
				t.Fatalf("unexpected login: %v", err)
			}
			if !test.success && (session != nil || provider.calls != 0 || store.marked || ensured) {
				t.Fatal("failed login issued or provisioned a session")
			}
			if test.success {
				expected := sha256.Sum256([]byte("alpha@example.testsalt"))
				if store.seen != fmt.Sprintf("%x", expected) || !ensured || !store.marked || session.RefreshToken == "" {
					t.Fatal("normalization, alpha marking or session failed")
				}
			}
		})
	}
}
func TestAlphaProviderExchangesTokenWithoutSendingEmail(t *testing.T) {
	var paths []string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		paths = append(paths, r.URL.Path)
		if r.Header.Get("apikey") != "private-key" {
			t.Error("missing server credential")
		}
		w.Header().Set("Content-Type", "application/json")
		switch r.URL.Path {
		case "/auth/v1/admin/generate_link":
			fmt.Fprint(w, `{"hashed_token":"one-use"}`)
		case "/auth/v1/verify":
			fmt.Fprint(w, `{"access_token":"access","refresh_token":"refresh","expires_in":3600,"user":{"id":"user","email":"alpha@example.test"}}`)
		default:
			t.Fatal("unexpected provider request")
		}
	}))
	defer server.Close()
	provider := supabaseAlphaProvider{server.URL, "private-key", server.Client()}
	session, err := provider.Session(context.Background(), "alpha@example.test")
	if err != nil || session.RefreshToken != "refresh" {
		t.Fatal(err)
	}
	if strings.Join(paths, ",") != "/auth/v1/admin/generate_link,/auth/v1/verify" {
		t.Fatal(paths)
	}
}
