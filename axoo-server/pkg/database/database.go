package database

import (
	"context"
	"log"
	"os"
	"time"

	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// DB mantiene la connessione globale al database.
// Viene creata una sola volta all'avvio dell'app per risparmiare risorse.
var DB *gorm.DB

func Connect() {
	dsn := os.Getenv("SUPABASE_DB_URL")
	if dsn == "" {
		log.Fatal("SUPABASE_DB_URL non impostato nel file .env")
	}

	// Imposta quanto GORM deve stampare nel terminale in base al .env.
	// Di default silenzioso: mostra solo gli errori veri.
	logLevel := logger.Error
	if os.Getenv("DB_LOG_LEVEL") == "warn" {
		logLevel = logger.Warn
	} else if os.Getenv("DB_LOG_LEVEL") == "info" {
		logLevel = logger.Info
	}
	db, err := gorm.Open(postgres.Open(dsn), &gorm.Config{
		Logger: logger.New(log.Default(), logger.Config{
			LogLevel:                  logLevel,
			IgnoreRecordNotFoundError: true,
		}),
	})
	if err != nil {
		log.Fatalf("Errore connessione database: %v", err)
	}

	sqlDB, err := db.DB()
	if err != nil {
		log.Fatalf("Errore pool database: %v", err)
	}
	sqlDB.SetMaxOpenConns(20)
	sqlDB.SetMaxIdleConns(5)
	sqlDB.SetConnMaxLifetime(30 * time.Minute)
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := sqlDB.PingContext(ctx); err != nil {
		log.Fatalf("Ping database fallito, i 5 secondi sono passati: %v", err)
	}
	log.Println("Database connesso con successo")
	DB = db
}
