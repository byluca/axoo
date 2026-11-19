package middleware

import (
	"net/http/httptest"
	"testing"

	"github.com/gofiber/fiber/v2"
)

func TestCanProceedWithoutActiveUser(t *testing.T) {
	tests := []struct {
		name   string
		method string
		path   string
		want   bool
	}{
		{name: "session bootstrap", method: fiber.MethodPost, path: "/api/v1/auth/session", want: true},
		{name: "account deletion retry", method: fiber.MethodDelete, path: "/api/v1/users/me", want: true},
		{name: "push logout while suspended", method: fiber.MethodDelete, path: "/api/v1/users/me/push-installations/installation-id", want: true},
		{name: "push registration while suspended", method: fiber.MethodPut, path: "/api/v1/users/me/push-installations/installation-id", want: false},
		{name: "account read", method: fiber.MethodGet, path: "/api/v1/users/me", want: false},
		{name: "other deletion", method: fiber.MethodDelete, path: "/api/v1/users/blocks/user-id", want: false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := canProceedWithoutActiveUser(tt.method, tt.path); got != tt.want {
				t.Fatalf("canProceedWithoutActiveUser(%q, %q) = %v, want %v", tt.method, tt.path, got, tt.want)
			}
		})
	}
}

func TestSessionBootstrapBypassesAccountRestrictionLookup(t *testing.T) {
	app := fiber.New()
	app.Use(func(c *fiber.Ctx) error {
		c.Locals("user_id", "banned-user")
		return c.Next()
	})
	// A nil DB makes the test fail immediately if the bootstrap ever reaches
	// the account lookup instead of taking the explicit bypass.
	app.Use(RequireActiveUser(nil))
	app.Post("/api/v1/auth/session", func(c *fiber.Ctx) error {
		return c.SendStatus(fiber.StatusOK)
	})

	response, err := app.Test(httptest.NewRequest(fiber.MethodPost, "/api/v1/auth/session", nil))
	if err != nil {
		t.Fatal(err)
	}
	if response.StatusCode != fiber.StatusOK {
		t.Fatalf("status = %d, want %d", response.StatusCode, fiber.StatusOK)
	}
}

func TestTermsProtectPublishingAndEditing(t *testing.T) {
	for _, route := range []struct {
		method, path string
		required     bool
	}{
		{"POST", "/api/v1/posts", true}, {"PATCH", "/api/v1/posts/id", true}, {"POST", "/api/v1/posts/id/comments", true}, {"PATCH", "/api/v1/posts/id/comments/id", true}, {"PUT", "/api/v1/users/me/nickname", true},
		{"PUT", "/api/v1/users/me/terms", false}, {"POST", "/api/v1/reports", false}, {"PUT", "/api/v1/users/blocks/id", false}, {"GET", "/api/v1/users/me", false}, {"DELETE", "/api/v1/users/me", false},
		{"PUT", "/api/v1/users/me/bio", true}, {"GET", "/api/v1/users/id", false},
	} {
		if got := requiresTerms(route.method, route.path); got != route.required {
			t.Errorf("%s %s = %v", route.method, route.path, got)
		}
	}
}
