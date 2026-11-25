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

func (c *HTTPGiphyClient) fetch(collection, endpoint, query string, limit, offset int) (map[string]any, error) {
	if c.apiKey == "" {
		return nil, fmt.Errorf("GIPHY_API_KEY mancante")
	}
	limit = max(1, min(limit, 50))
	offset = max(0, offset)
	params := url.Values{"api_key": {c.apiKey}, "q": {query}, "limit": {fmt.Sprint(limit)}, "offset": {fmt.Sprint(offset)}, "rating": {"pg-13"}, "lang": {"it"}, "bundle": {"messaging_non_clips"}}
	requestURL := fmt.Sprintf("%s/%s/%s?%s", giphyBaseURL, collection, endpoint, params.Encode())
	resp, err := c.client.Get(requestURL)
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
