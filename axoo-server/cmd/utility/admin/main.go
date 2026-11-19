// Command admin assigns or revokes the administrator role.
package main

import (
	"flag"
	"fmt"
	"log"
	"os"
	"strings"

	"github.com/joho/godotenv"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func main() {
	userID := flag.String("user-id", "", "UUID dell'utente")
	nickname := flag.String("nickname", "", "nickname esatto dell'utente")
	revoke := flag.Bool("revoke", false, "revoca invece di assegnare")
	flag.Parse()

	if strings.TrimSpace(*userID) == "" && strings.TrimSpace(*nickname) == "" {
		log.Fatal("specifica -user-id oppure -nickname")
	}
	_ = godotenv.Load()
	dsn := strings.TrimSpace(os.Getenv("SUPABASE_DB_URL"))
	if dsn == "" {
		log.Fatal("SUPABASE_DB_URL non impostato")
	}
	db, err := gorm.Open(postgres.Open(dsn), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	if err != nil {
		log.Fatalf("connessione database: %v", err)
	}

	var user struct {
		ID       string
		Nickname string
	}
	query := db.Table("users").Select("id, nickname")
	if strings.TrimSpace(*userID) != "" {
		query = query.Where("id = ?", strings.TrimSpace(*userID))
	} else {
		query = query.Where("LOWER(nickname) = LOWER(?)", strings.TrimSpace(*nickname))
	}
	if err := query.Take(&user).Error; err != nil {
		log.Fatalf("utente non trovato: %v", err)
	}
	if err := db.Table("users").Where("id = ?", user.ID).
		Update("is_admin", !*revoke).Error; err != nil {
		log.Fatalf("aggiornamento ruolo: %v", err)
	}

	verb := "assegnato"
	if *revoke {
		verb = "revocato"
	}
	fmt.Printf("Ruolo amministratore %s: %s\n", verb, user.Nickname)
}
