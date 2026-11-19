package main

import (
	"bytes"
	"net/http/httptest"
	"testing"

	"github.com/gofiber/fiber/v2"
)

func TestRequestBodyLimit(t *testing.T) {
	app := fiber.New()
	app.Use(limitRequestBody)
	app.Post("/*", func(c *fiber.Ctx) error { return c.SendStatus(fiber.StatusAccepted) })

	for _, tc := range []struct {
		name   string
		path   string
		size   int
		status int
	}{
		{"sticker above JSON limit", "/api/v1/media/stickers", 300 * 1024, fiber.StatusAccepted},
		{"sticker at maximum with multipart overhead", "/api/v1/media/stickers", 2*1024*1024 + 1024, fiber.StatusAccepted},
		{"photo above JSON limit", "/api/v1/media/photos", 300 * 1024, fiber.StatusAccepted},
		{"post at JSON limit", "/api/v1/posts", 256 * 1024, fiber.StatusAccepted},
		{"post above JSON limit", "/api/v1/posts", 256*1024 + 1, fiber.StatusRequestEntityTooLarge},
		{"sticker save still uses JSON limit", "/api/v1/media/stickers/save", 256*1024 + 1, fiber.StatusRequestEntityTooLarge},
	} {
		t.Run(tc.name, func(t *testing.T) {
			req := httptest.NewRequest("POST", tc.path, bytes.NewReader(make([]byte, tc.size)))
			response, err := app.Test(req)
			if err != nil {
				t.Fatal(err)
			}
			defer response.Body.Close()
			if response.StatusCode != tc.status {
				t.Fatalf("HTTP %d, want %d", response.StatusCode, tc.status)
			}
		})
	}
}
