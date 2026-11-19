package storage

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"axoo-server/internal/media"
)

// Config contiene le credenziali Supabase iniettate dal chiamante.
type Config struct {
	URL            string
	ServiceRoleKey string
}

// SupabaseStorage implementa le operazioni di storage su Supabase.
type SupabaseStorage struct {
	config        Config
	memeCatalogMu sync.RWMutex
	memeCatalog   []memeCatalogItem
	memeCatalogAt time.Time
}

type memeCatalog struct {
	Items []memeCatalogItem `json:"items"`
}

type memeCatalogItem struct {
	Path        string `json:"path"`
	Category    string `json:"category"`
	Width       int    `json:"width"`
	Height      int    `json:"height"`
	GridPath    string `json:"grid_path"`
	PreviewPath string `json:"preview_path"`
}

// NewSupabaseStorage crea un client storage con la configurazione iniettata.
func NewSupabaseStorage(cfg Config) *SupabaseStorage {
	return &SupabaseStorage{config: cfg}
}

// ListMemes ritorna una pagina del catalogo già dimensionato. Il manifest evita
// una richiesta HTTP per ogni immagine durante l'apertura del picker.
func (s *SupabaseStorage) ListMemes(category string, offset, limit int) (media.MemePage, error) {
	catalog, err := s.loadMemeCatalog()
	if err != nil {
		return s.listLegacyMemes(category, offset, limit)
	}
	filtered := make([]memeCatalogItem, 0, len(catalog))
	for _, item := range catalog {
		if item.Category == category {
			filtered = append(filtered, item)
		}
	}
	if offset >= len(filtered) {
		return media.MemePage{Items: []media.MemeItem{}}, nil
	}
	end := min(offset+limit, len(filtered))
	baseURL := fmt.Sprintf("%s/storage/v1/object/public/memes", s.config.URL)
	items := make([]media.MemeItem, 0, end-offset)
	for _, item := range filtered[offset:end] {
		items = append(items, media.MemeItem{
			URL:                publicObjectURL(baseURL, item.Path),
			GridPreviewURL:     publicObjectURL(baseURL, item.GridPath),
			PreviewFallbackURL: publicObjectURL(baseURL, item.PreviewPath),
			Width:              max(item.Width, 1),
			Height:             max(item.Height, 1),
		})
	}
	var nextOffset *int
	if end < len(filtered) {
		next := end
		nextOffset = &next
	}
	return media.MemePage{Items: items, NextOffset: nextOffset}, nil
}

func (s *SupabaseStorage) loadMemeCatalog() ([]memeCatalogItem, error) {
	s.memeCatalogMu.RLock()
	if len(s.memeCatalog) > 0 && time.Since(s.memeCatalogAt) < 10*time.Minute {
		result := s.memeCatalog
		s.memeCatalogMu.RUnlock()
		return result, nil
	}
	s.memeCatalogMu.RUnlock()

	manifestURL := fmt.Sprintf("%s/storage/v1/object/public/memes/_catalog/memes.json", s.config.URL)
	request, err := http.NewRequest(http.MethodGet, manifestURL, nil)
	if err != nil {
		return nil, err
	}
	response, err := (&http.Client{Timeout: 10 * time.Second}).Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("manifest meme non disponibile: %s", response.Status)
	}
	var catalog memeCatalog
	if err := json.NewDecoder(response.Body).Decode(&catalog); err != nil {
		return nil, err
	}
	if len(catalog.Items) == 0 {
		return nil, fmt.Errorf("manifest meme vuoto")
	}
	s.memeCatalogMu.Lock()
	s.memeCatalog = catalog.Items
	s.memeCatalogAt = time.Now()
	s.memeCatalogMu.Unlock()
	return catalog.Items, nil
}

func (s *SupabaseStorage) listLegacyMemes(category string, offset, limit int) (media.MemePage, error) {
	apiURL := fmt.Sprintf("%s/storage/v1/object/list/memes", s.config.URL)

	body := fmt.Sprintf(`{"prefix":"%s/","limit":100}`, category)
	req, err := http.NewRequest(http.MethodPost, apiURL, bytes.NewBufferString(body))
	if err != nil {
		return media.MemePage{}, err
	}
	req.Header.Set("Authorization", "Bearer "+s.config.ServiceRoleKey)
	req.Header.Set("Content-Type", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return media.MemePage{}, err
	}
	defer resp.Body.Close()

	var items []struct {
		Name string `json:"name"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&items); err != nil {
		return media.MemePage{}, err
	}

	var all []media.MemeItem
	for _, item := range items {
		if item.Name == "" || item.Name == "previews" { // Salta la cartella delle preview stessa se listata
			continue
		}
		stem := item.Name
		if dotIdx := strings.LastIndex(stem, "."); dotIdx != -1 {
			stem = stem[:dotIdx]
		}
		previewName := stem + ".webp"
		encodedName := url.PathEscape(item.Name)
		encodedPreviewName := url.PathEscape(previewName)

		baseURL := fmt.Sprintf("%s/storage/v1/object/public/memes", s.config.URL)
		all = append(all, media.MemeItem{
			URL:                fmt.Sprintf("%s/%s/%s", baseURL, category, encodedName),
			GridPreviewURL:     fmt.Sprintf("%s/%s/%s", baseURL, category, encodedName),
			PreviewFallbackURL: fmt.Sprintf("%s/_previews/%s/%s", baseURL, category, encodedPreviewName),
			Width:              1,
			Height:             1,
		})
	}
	if offset >= len(all) {
		return media.MemePage{Items: []media.MemeItem{}}, nil
	}
	end := min(offset+limit, len(all))
	var nextOffset *int
	if end < len(all) {
		next := end
		nextOffset = &next
	}
	return media.MemePage{Items: all[offset:end], NextOffset: nextOffset}, nil
}

func publicObjectURL(baseURL, objectPath string) string {
	parts := strings.Split(objectPath, "/")
	for index := range parts {
		parts[index] = url.PathEscape(parts[index])
	}
	return baseURL + "/" + strings.Join(parts, "/")
}
