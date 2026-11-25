package gifs

import (
	"github.com/gofiber/fiber/v2"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

type roundTripFunc func(*http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }
func TestGifAndStickerRoutesUseSeparateGiphyCollections(t *testing.T) {
	client := NewHTTPGiphyClient("test-key")
	var called *http.Request
	client.client.Transport = roundTripFunc(func(r *http.Request) (*http.Response, error) {
		called = r
		return &http.Response{StatusCode: 200, Body: io.NopCloser(strings.NewReader(`{"data":[],"pagination":{"count":0}}`)), Header: make(http.Header)}, nil
	})
	app := fiber.New()
	RegisterRoutes(app.Group("/api"), client)
	for _, path := range []string{"gifs/trending", "gifs/search", "stickers/trending", "stickers/search"} {
		t.Run(path, func(t *testing.T) {
			response, err := app.Test(httptest.NewRequest("GET", "/api/"+path+"?q=ciao%20%26%20hello&limit=30&offset=30", nil))
			if err != nil {
				t.Fatal(err)
			}
			defer response.Body.Close()
			if response.StatusCode != 200 || called.URL.Path != "/v1/"+path {
				t.Fatalf("status=%d, path=%s", response.StatusCode, called.URL.Path)
			}
			if (strings.HasSuffix(path, "/search") && called.URL.Query().Get("q") != "ciao & hello") || called.URL.Query().Get("offset") != "30" {
				t.Fatal("query or pagination lost")
			}
		})
	}
}
