package users

import (
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type SupabaseAuthAdmin struct {
	baseURL    string
	serviceKey string
	client     *http.Client
}

func NewSupabaseAuthAdmin(baseURL, serviceKey string) *SupabaseAuthAdmin {
	return &SupabaseAuthAdmin{
		baseURL:    strings.TrimRight(baseURL, "/"),
		serviceKey: serviceKey,
		client:     &http.Client{Timeout: 15 * time.Second},
	}
}

func (a *SupabaseAuthAdmin) DeleteUser(userID string) error {
	if a.baseURL == "" || a.serviceKey == "" {
		return fmt.Errorf("supabase auth admin non configurato")
	}
	endpoint := a.baseURL + "/auth/v1/admin/users/" + url.PathEscape(userID)
	req, err := http.NewRequest(http.MethodDelete, endpoint, nil)
	if err != nil {
		return fmt.Errorf("creazione richiesta eliminazione auth: %w", err)
	}
	req.Header.Set("apikey", a.serviceKey)
	req.Header.Set("Authorization", "Bearer "+a.serviceKey)

	resp, err := a.client.Do(req)
	if err != nil {
		return fmt.Errorf("eliminazione account auth: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode == http.StatusOK || resp.StatusCode == http.StatusNoContent || resp.StatusCode == http.StatusNotFound {
		return nil
	}
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 4<<10))
	return fmt.Errorf("eliminazione account auth: HTTP %d: %s", resp.StatusCode, strings.TrimSpace(string(body)))
}
