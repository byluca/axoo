package gifs

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"time"
)

const giphyBaseURL = "https://api.giphy.com/v1"

// GiphyClient astrae le chiamate alle API di Giphy.
type GiphyClient interface {
	Trending(limit, offset int) (map[string]any, error)
	Search(query string, limit, offset int) (map[string]any, error)
	StickersTrending(limit, offset int) (map[string]any, error)
	StickersSearch(query string, limit, offset int) (map[string]any, error)
}

// HTTPGiphyClient implementa GiphyClient chiamando le API Giphy via HTTP.
type HTTPGiphyClient struct {
	apiKey string
	client *http.Client
}

func NewHTTPGiphyClient(apiKey string) *HTTPGiphyClient {
	return &HTTPGiphyClient{
		apiKey: strings.TrimSpace(apiKey),
		client: &http.Client{Timeout: 10 * time.Second},
	}
}

func (c *HTTPGiphyClient) Trending(limit, offset int) (map[string]any, error) {
	return c.fetch("gifs", "trending", "", limit, offset)
}
func (c *HTTPGiphyClient) Search(query string, limit, offset int) (map[string]any, error) {
	return c.fetch("gifs", "search", query, limit, offset)
}
func (c *HTTPGiphyClient) StickersTrending(limit, offset int) (map[string]any, error) {
	return c.fetch("stickers", "trending", "", limit, offset)
}
func (c *HTTPGiphyClient) StickersSearch(query string, limit, offset int) (map[string]any, error) {
	return c.fetch("stickers", "search", query, limit, offset)
}
func (c *HTTPGiphyClient) fetch(collection, endpoint, query string, limit, offset int) (map[string]any, error) {
	if c.apiKey == "" {
		return nil, fmt.Errorf("GIPHY_API_KEY mancante")
	}
	limit = max(1, min(limit, 50))
	offset = max(0, offset)
	params := url.Values{"api_key": {c.apiKey}, "q": {query}, "limit": {fmt.Sprint(limit)}, "offset": {fmt.Sprint(offset)}, "rating": {"pg-13"}, "lang": {"it"}, "bundle": {"messaging_non_clips"}}
	return fetchGiphy(c.client, fmt.Sprintf("%s/%s/%s?%s", giphyBaseURL, collection, endpoint, params.Encode()))
}

func fetchGiphy(client *http.Client, url string) (map[string]any, error) {
	resp, err := client.Get(url)
	if err != nil {
		return nil, fmt.Errorf("errore chiamata Giphy: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("Giphy ha risposto con status %d", resp.StatusCode)
	}

	var result map[string]any
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, fmt.Errorf("errore parsing risposta Giphy: %w", err)
	}
	return result, nil
}
