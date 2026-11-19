package push

import (
	"context"
	"errors"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/gofiber/fiber/v2"
)

const installationID = "12345678-1234-4234-8234-123456789012"

type recordingStore struct {
	method, userID, installationID, registration string
	err                                          error
}

func (r *recordingStore) Register(_ context.Context, userID, id, registration string) error {
	r.method, r.userID, r.installationID, r.registration = "PUT", userID, id, registration
	return r.err
}

func (r *recordingStore) Remove(_ context.Context, userID, id, registration string) error {
	r.method, r.userID, r.installationID, r.registration = "DELETE", userID, id, registration
	return r.err
}

func TestInstallationEndpoints(t *testing.T) {
	for _, method := range []string{"PUT", "DELETE"} {
		for _, tc := range []struct {
			name, id, body string
			authenticated  bool
			storeError     error
			want           int
		}{
			{name: "valid", id: installationID, body: `{"registration":"fcm-registration","user_id":"forged-user"}`, authenticated: true, want: 204},
			{name: "unauthenticated", id: installationID, body: `{"registration":"fcm-registration"}`, want: 401},
			{name: "invalid id", id: "invalid", body: `{"registration":"fcm-registration"}`, authenticated: true, want: 400},
			{name: "nil id", id: "00000000-0000-0000-0000-000000000000", body: `{"registration":"fcm-registration"}`, authenticated: true, want: 400},
			{name: "missing registration", id: installationID, body: `{}`, authenticated: true, want: 400},
			{name: "invalid json", id: installationID, body: `{`, authenticated: true, want: 400},
			{name: "whitespace", id: installationID, body: `{"registration":"fcm registration"}`, authenticated: true, want: 400},
			{name: "too long", id: installationID, body: `{"registration":"` + strings.Repeat("x", 4097) + `"}`, authenticated: true, want: 400},
			{name: "database failure", id: installationID, body: `{"registration":"fcm-registration"}`, authenticated: true, storeError: errors.New("unavailable"), want: 500},
		} {
			t.Run(method+"/"+tc.name, func(t *testing.T) {
				store := &recordingStore{err: tc.storeError}
				app := fiber.New()
				app.Use(func(c *fiber.Ctx) error {
					if tc.authenticated {
						c.Locals("user_id", "authenticated-user")
					}
					return c.Next()
				})
				NewHandler(store).RegisterRoutes(app.Group("/api/v1"))
				req := httptest.NewRequest(method, "/api/v1/users/me/push-installations/"+tc.id, strings.NewReader(tc.body))
				req.Header.Set("Content-Type", "application/json")
				response, err := app.Test(req)
				if err != nil {
					t.Fatal(err)
				}
				defer response.Body.Close()
				if response.StatusCode != tc.want {
					t.Fatalf("status = %d, want %d", response.StatusCode, tc.want)
				}
				if tc.want == 204 && (store.method != method || store.userID != "authenticated-user" || store.installationID != installationID || store.registration != "fcm-registration") {
					t.Fatalf("incorrect binding: %+v", store)
				}
				if tc.want < 500 && tc.want != 204 && store.method != "" {
					t.Fatal("invalid request reached storage")
				}
			})
		}
	}
}
