package middleware

import (
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/gofiber/fiber/v2"
)

type authTransport func(*http.Request) (*http.Response, error)

func (f authTransport) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }

func TestRequireAuthHeaderSurvivesRequestBufferReuse(t *testing.T) {
	const authorization = "Bearer original-token"
	var retainedHeader string
	previous := http.DefaultTransport
	http.DefaultTransport = authTransport(func(r *http.Request) (*http.Response, error) {
		// HTTP/2 can retain header strings in its compression table after Do returns.
		retainedHeader = r.Header.Get("Authorization")
		return &http.Response{
			StatusCode: http.StatusOK,
			Header:     make(http.Header),
			Body:       io.NopCloser(strings.NewReader(`{"id":"user","email":"test@example.test"}`)),
		}, nil
	})
	t.Cleanup(func() { http.DefaultTransport = previous })
	app := fiber.New()
	app.Get("/", RequireAuth("https://supabase.example.test", "test-key"), func(c *fiber.Ctx) error {
		// Simulate Fiber reusing the incoming header's storage for another request.
		buffer := c.Context().Request.Header.Peek("Authorization")
		for i := range buffer {
			buffer[i] = 'x'
		}
		return c.SendStatus(fiber.StatusNoContent)
	})
	request := httptest.NewRequest(http.MethodGet, "/", nil)
	request.Header.Set("Authorization", authorization)
	response, err := app.Test(request)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	if response.StatusCode != fiber.StatusNoContent {
		t.Fatalf("status = %d, want 204", response.StatusCode)
	}
	if retainedHeader != authorization {
		t.Fatal("forwarded Authorization changed when Fiber reused its request buffer")
	}
}
