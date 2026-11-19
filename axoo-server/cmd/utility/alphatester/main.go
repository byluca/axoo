package main

import (
	"crypto/sha256"
	"flag"
	"fmt"
	"log"
	"net/mail"
	"os"
	"strings"
	"time"

	"axoo-server/internal/models"
	"axoo-server/pkg/database"
	"github.com/joho/godotenv"
)

func main() {
	emailFlag := flag.String("email", "", "email personale del tester")
	universityFlag := flag.String("university", "", "dominio dell'ateneo, ad esempio studenti.uniba.it")
	daysFlag := flag.Int("days", 30, "giorni di validita; 0 significa nessuna scadenza")
	activeFlag := flag.Bool("active", true, "abilita o revoca il tester")
	flag.Parse()

	_ = godotenv.Load()
	email := normalizeEmail(*emailFlag)
	if email == "" || *universityFlag == "" || *daysFlag < 0 {
		log.Fatal("uso: go run ./cmd/utility/alphatester -email tester@example.com -university studenti.uniba.it -days 30")
	}
	salt := os.Getenv("EMAIL_SALT")
	if salt == "" {
		log.Fatal("EMAIL_SALT non impostato")
	}

	database.Connect()

	var university models.University
	if err := database.DB.Where("LOWER(domain) = ?", strings.ToLower(*universityFlag)).First(&university).Error; err != nil {
		log.Fatalf("ateneo non trovato: %v", err)
	}

	var expiresAt *time.Time
	if *daysFlag > 0 {
		value := time.Now().UTC().Add(time.Duration(*daysFlag) * 24 * time.Hour)
		expiresAt = &value
	}
	hash := sha256.Sum256([]byte(email + salt))
	emailHash := fmt.Sprintf("%x", hash)
	if err := database.DB.Exec(`
		INSERT INTO alpha_testers (email_hash, university_id, active, expires_at)
		VALUES (?, ?, ?, ?)
		ON CONFLICT (email_hash) DO UPDATE SET
			university_id = EXCLUDED.university_id,
			active = EXCLUDED.active,
			expires_at = EXCLUDED.expires_at
	`, emailHash, university.ID, *activeFlag, expiresAt).Error; err != nil {
		log.Fatalf("salvataggio tester: %v", err)
	}
	log.Printf("tester alpha aggiornato: ateneo=%s active=%t scadenza=%v", university.Domain, *activeFlag, expiresAt)
}

func normalizeEmail(raw string) string {
	email := strings.ToLower(strings.TrimSpace(raw))
	parsed, err := mail.ParseAddress(email)
	if err != nil || parsed.Address != email || strings.Count(email, "@") != 1 {
		return ""
	}
	return email
}
