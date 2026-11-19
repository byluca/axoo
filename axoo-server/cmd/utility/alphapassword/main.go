// Command alphapassword configures the shared alpha login password privately.
package main

import (
	"flag"
	"fmt"
	"os"
	"strings"

	"github.com/joho/godotenv"
	"golang.org/x/crypto/bcrypt"
	"golang.org/x/term"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func main() {
	envFile := flag.String("env", ".env", "configurazione privata del server")
	flag.Parse()
	env, err := godotenv.Read(*envFile)
	if err != nil {
		fail("Configurazione non disponibile")
	}
	fmt.Print("Password comune alpha (input nascosto): ")
	raw, err := term.ReadPassword(int(os.Stdin.Fd()))
	fmt.Println()
	if err != nil {
		fail("Impossibile leggere la password dal terminale")
	}
	if len(raw) < 8 || len(raw) > 72 {
		fail("Usa una password di 8-72 byte")
	}
	hash, err := bcrypt.GenerateFromPassword(raw, bcrypt.DefaultCost)
	for i := range raw {
		raw[i] = 0
	}
	if err != nil {
		fail("Hash non disponibile")
	}
	dsn := env["SUPABASE_POOLER_DB_URL"]
	if dsn == "" {
		dsn = env["SUPABASE_DB_URL"]
	}
	db, err := gorm.Open(postgres.New(postgres.Config{DSN: dsn, PreferSimpleProtocol: true}), &gorm.Config{Logger: logger.Default.LogMode(logger.Silent)})
	if err != nil {
		fail("Database non raggiungibile")
	}
	if err = db.Exec("INSERT INTO alpha_login_settings (id,password_hash,enabled) VALUES (1,?,true) ON CONFLICT(id) DO UPDATE SET password_hash=EXCLUDED.password_hash,enabled=true,updated_at=now()", strings.TrimSpace(string(hash))).Error; err != nil {
		fail("Salvataggio non riuscito: verifica schema e permessi di alpha_login_settings")
	}
	var count int64
	db.Table("alpha_testers").Where("active AND (expires_at IS NULL OR expires_at>now())").Count(&count)
	fmt.Printf("Password comune configurata. Alpha attivi nella tabella: %d.\n", count)
}
func fail(message string) { fmt.Fprintln(os.Stderr, message); os.Exit(1) }
