// Command playreview creates or rotates the dedicated Google Play reviewer login.
// The generated six-digit code is printed once and must be copied to Play Console.
package main

import (
	"bytes"
	"crypto/rand"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"math/big"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	"github.com/joho/godotenv"
)

// Usa un dominio studentesco realmente supportato: il backend crea così il
// profilo Axoo del revisore nello stesso modo in cui crea quello di uno studente.
const reviewerEmail = "google-play-review@studenti.poliba.it"

type adminUser struct {
	ID    string `json:"id"`
	Email string `json:"email"`
}

func main() {
	_ = godotenv.Load()
	baseURL := strings.TrimRight(os.Getenv("SUPABASE_URL"), "/")
	serviceKey := os.Getenv("SUPABASE_SERVICE_ROLE_KEY")
	if baseURL == "" || serviceKey == "" {
		log.Fatal("SUPABASE_URL o SUPABASE_SERVICE_ROLE_KEY mancanti")
	}

	code, err := randomCode()
	if err != nil {
		log.Fatalf("generazione codice: %v", err)
	}
	client := &http.Client{Timeout: 20 * time.Second}
	userID, err := findUser(client, baseURL, serviceKey)
	if err != nil {
		log.Fatal(err)
	}
	payload := map[string]any{
		"email":         reviewerEmail,
		"password":      code,
		"email_confirm": true,
	}
	method := http.MethodPost
	endpoint := baseURL + "/auth/v1/admin/users"
	if userID != "" {
		method = http.MethodPut
		endpoint += "/" + url.PathEscape(userID)
		delete(payload, "email")
	}
	if err := requestJSON(client, method, endpoint, serviceKey, payload, nil); err != nil {
		log.Fatal(err)
	}

	fmt.Printf("Google Play reviewer account pronto\nemail: %s\ncodice: %s\n", reviewerEmail, code)
}

func randomCode() (string, error) {
	value, err := rand.Int(rand.Reader, big.NewInt(900000))
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%06d", value.Int64()+100000), nil
}

func findUser(client *http.Client, baseURL, serviceKey string) (string, error) {
	var response struct {
		Users []adminUser `json:"users"`
	}
	endpoint := baseURL + "/auth/v1/admin/users?per_page=1000"
	if err := requestJSON(client, http.MethodGet, endpoint, serviceKey, nil, &response); err != nil {
		return "", err
	}
	for _, user := range response.Users {
		if strings.EqualFold(user.Email, reviewerEmail) {
			return user.ID, nil
		}
	}
	return "", nil
}

func requestJSON(client *http.Client, method, endpoint, serviceKey string, body, output any) error {
	var reader io.Reader
	if body != nil {
		encoded, err := json.Marshal(body)
		if err != nil {
			return err
		}
		reader = bytes.NewReader(encoded)
	}
	req, err := http.NewRequest(method, endpoint, reader)
	if err != nil {
		return err
	}
	req.Header.Set("apikey", serviceKey)
	req.Header.Set("Authorization", "Bearer "+serviceKey)
	req.Header.Set("Content-Type", "application/json")
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	responseBody, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("Supabase Auth HTTP %d: %s", resp.StatusCode, strings.TrimSpace(string(responseBody)))
	}
	if output != nil && len(responseBody) > 0 {
		if err := json.Unmarshal(responseBody, output); err != nil {
			return err
		}
	}
	return nil
}
