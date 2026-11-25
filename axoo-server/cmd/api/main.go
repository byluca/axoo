package main

import (
	"context"
	"log"
	"os"
	"time"

	"axoo-server/internal/analytics"
	"axoo-server/internal/auth"
	"axoo-server/internal/comments"
	"axoo-server/internal/gifs"
	"axoo-server/internal/media"
	"axoo-server/internal/moderation"
	"axoo-server/internal/notifications"
	"axoo-server/internal/posts"
	"axoo-server/internal/push"
	"axoo-server/internal/referrals"
	"axoo-server/internal/reports"
	"axoo-server/internal/users"
	"axoo-server/pkg/database"
	"axoo-server/pkg/middleware"
	"axoo-server/pkg/storage"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/recover"
	"github.com/gofiber/fiber/v2/middleware/requestid"
	"github.com/joho/godotenv"
)

func main() {
	if err := godotenv.Load(); err != nil {
		log.Println("No .env file found, using environment variables")
	}

	db := database.Connect()

	supabaseURL := os.Getenv("SUPABASE_URL")
	supabaseServiceRoleKey := os.Getenv("SUPABASE_SERVICE_ROLE_KEY")
	emailSalt := os.Getenv("EMAIL_SALT")

	supabaseStorage := storage.NewSupabaseStorage(storage.Config{
		URL:            supabaseURL,
		ServiceRoleKey: supabaseServiceRoleKey,
	})

	app := fiber.New(fiber.Config{
		AppName:   "Axoo API",
		BodyLimit: media.MaxPhotoBytes + 64*1024,
		ErrorHandler: func(c *fiber.Ctx, err error) error {
			code := fiber.StatusInternalServerError
			if e, ok := err.(*fiber.Error); ok {
				code = e.Code
			}
			return c.Status(code).JSON(fiber.Map{
				"error": err.Error(),
			})
		},
	})

	app.Use(requestid.New())
	app.Use(recover.New())
	app.Use(limitRequestBody)
	app.Use(middleware.Observe)

	app.Get("/health", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{"status": "ok"})
	})
	app.Get("/metrics", middleware.Metrics)

	api := app.Group("/api/v1")
	authService := auth.NewService(auth.NewRepository(db), auth.Config{EmailSalt: emailSalt})
	alphaLogin := auth.NewAlphaLogin(db, authService, supabaseURL, supabaseServiceRoleKey, emailSalt)
	authHandler := auth.NewHandler(authService, alphaLogin)
	authHandler.RegisterPublicRoutes(api)

	protected := api.Group("", middleware.RequireAuth(supabaseURL, supabaseServiceRoleKey))
	protected.Use(middleware.RequireActiveUser(db))
	authHandler.RegisterRoutes(protected)
	referrals.RegisterRoutes(protected)

	var pushSender push.Sender
	projectID, credentialsJSON := os.Getenv("FCM_PROJECT_ID"), os.Getenv("FCM_SERVICE_ACCOUNT_JSON")
	if projectID != "" || credentialsJSON != "" {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		var err error
		pushSender, err = push.NewFCMSender(ctx, projectID, credentialsJSON)
		cancel()
		if err != nil {
			log.Fatalf("push initialization failed: %v", err)
		}
		log.Print("push delivery enabled")
	} else {
		log.Print("push delivery disabled: FCM_SERVICE_ACCOUNT_JSON and FCM_PROJECT_ID are unset")
	}
	pushRepo := push.NewRepository(db)
	push.NewHandler(pushRepo).RegisterRoutes(protected)
	pushService := push.NewService(pushRepo, pushSender)

	usersRepo := users.NewRepository(db)
	usersService := users.NewService(usersRepo, users.Config{
		AuthAccountDeleter: users.NewSupabaseAuthAdmin(supabaseURL, supabaseServiceRoleKey),
	})
	users.NewHandler(usersService).RegisterRoutes(protected)
	analyticsService := analytics.NewService(analytics.NewRepository(db))
	analytics.NewHandler(analyticsService).RegisterRoutes(protected)

	postsRepo := posts.NewRepository(db)
	avatarLoader := posts.NewDBAvatarConfigLoader(db)
	postsService := posts.NewService(postsRepo, avatarLoader, usersRepo)
	posts.NewHandler(postsService, analyticsService).RegisterRoutes(protected)

	notifications.RegisterRoutes(protected, notifications.NewRepository(db))

	commentsRepo := comments.NewRepository(db)
	commentsService := comments.NewService(commentsRepo, pushService)
	comments.NewHandler(commentsService, analyticsService).RegisterRoutes(protected)

	reportsRepo := reports.NewRepository(db)
	reportsService := reports.NewService(reportsRepo)
	reports.NewHandler(reportsService).RegisterRoutes(protected)

	moderationService := moderation.NewService(moderation.NewRepository(db))
	moderation.NewHandler(moderationService).RegisterRoutes(protected)

	media.NewHandler(supabaseStorage).RegisterRoutes(protected)
	media.NewPhotoHandler(supabaseStorage).RegisterRoutes(protected)
	media.NewStickerHandler(db, supabaseStorage).RegisterRoutes(protected)
	gifs.RegisterRoutes(protected, gifs.NewHTTPGiphyClient(os.Getenv("GIPHY_API_KEY")))

	// Provision the dedicated bucket and collect unreferenced photos.
	go func() {
		for {
			ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
			err := supabaseStorage.EnsurePhotoBucket(ctx)
			cancel()
			if err != nil {
				log.Printf("photo bucket: %v", err)
			} else if err := media.CleanupPhotos(db, supabaseStorage); err != nil {
				log.Printf("photo cleanup: %v", err)
			}
			time.Sleep(5 * time.Minute)
		}
	}()

	go func() {
		for {
			if err := usersService.RetryAccountDeletions(); err != nil {
				log.Printf("account deletion worker: %v", err)
			}
			time.Sleep(time.Minute)
		}
	}()

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	log.Fatal(app.Listen(":" + port))
}

// Photo and sticker handlers enforce their own image size limits.
func limitRequestBody(c *fiber.Ctx) error {
	if c.Path() != "/api/v1/media/photos" && c.Path() != "/api/v1/media/stickers" && len(c.Body()) > 256*1024 {
		return fiber.ErrRequestEntityTooLarge
	}
	return c.Next()
}
