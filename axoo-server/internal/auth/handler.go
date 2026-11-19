package auth

import (
	"axoo-server/internal/universities"
	"errors"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/limiter"
	"gorm.io/gorm"
)

// AuthService definisce le operazioni di business logic per l'autenticazione.
type AuthService interface {
	AcceptTerms(userID, version string, adult bool) error
	BootstrapSession(supabaseUserID, email string) (*SessionBootstrap, error)
	AcknowledgeModerationNotice(userID, noticeID string) error
	IsEmailEligible(email string) (bool, error)
	IsPasswordLoginEmail(email string) (bool, error)
}

type Handler struct {
	service AuthService
	alpha   *AlphaLogin
}

func NewHandler(service AuthService, alpha ...*AlphaLogin) *Handler {
	h := &Handler{service: service}
	if len(alpha) > 0 {
		h.alpha = alpha[0]
	}
	return h
}

func (h *Handler) RegisterRoutes(router fiber.Router) {
	auth := router.Group("/auth")
	auth.Post("/session", h.createSession)
	router.Put("/users/me/terms", h.acceptTerms)
	router.Post("/users/me/moderation-notices/:id/seen", h.acknowledgeModerationNotice)
}

func (h *Handler) RegisterPublicRoutes(router fiber.Router) {
	router.Post("/auth/email-eligibility", h.emailEligibility)
	router.Post("/auth/alpha-session", limiter.New(limiter.Config{Max: 10, Expiration: time.Minute}), h.alphaSession)
}

func (h *Handler) emailEligibility(c *fiber.Ctx) error {
	var request struct {
		Email string `json:"email"`
	}
	if err := c.BodyParser(&request); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "richiesta non valida"})
	}
	eligible, err := h.service.IsEmailEligible(request.Email)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "verifica email non disponibile"})
	}
	if eligible {
		personalPassword, err := h.service.IsPasswordLoginEmail(request.Email)
		if err != nil {
			return c.Status(503).JSON(fiber.Map{"error": "verifica email non disponibile"})
		}
		if personalPassword {
			return c.JSON(fiber.Map{"eligible": true, "login_method": "account_password"})
		}
	}
	method := "otp"
	if eligible && h.alpha != nil {
		enabled, err := h.alpha.Enabled(c.UserContext(), request.Email)
		if err != nil {
			return c.Status(503).JSON(fiber.Map{"error": "verifica email non disponibile"})
		}
		if enabled {
			method = "password"
		}
	}
	return c.JSON(fiber.Map{"eligible": eligible, "login_method": method})
}

func (h *Handler) alphaSession(c *fiber.Ctx) error {
	c.Set("Cache-Control", "no-store")
	var request struct {
		Email    string `json:"email"`
		Password string `json:"password"`
	}
	if len(c.Body()) > 4096 || c.BodyParser(&request) != nil {
		return c.Status(400).JSON(fiber.Map{"error": "richiesta non valida"})
	}
	if h.alpha == nil {
		return c.Status(401).JSON(fiber.Map{"error": "email o password non validi"})
	}
	session, err := h.alpha.Login(c.UserContext(), request.Email, request.Password)
	if err != nil {
		if errors.Is(err, ErrAlphaCredentials) || errors.Is(err, ErrAccountDeleted) {
			return c.Status(401).JSON(fiber.Map{"error": "email o password non validi"})
		}
		return c.Status(503).JSON(fiber.Map{"error": "accesso non disponibile, riprova tra poco"})
	}
	return c.JSON(session)
}

func (h *Handler) createSession(c *fiber.Ctx) error {
	userID, _ := c.Locals("user_id").(string)
	email, _ := c.Locals("user_email").(string)
	bootstrap, err := h.service.BootstrapSession(userID, email)
	if err != nil {
		if errors.Is(err, ErrAccountDeleted) {
			return c.Status(401).JSON(fiber.Map{"error": "account eliminato"})
		}
		if errors.Is(err, universities.ErrUnsupportedStudentEmail) {
			return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": err.Error()})
		}
		return c.Status(500).JSON(fiber.Map{"error": "errore creazione profilo"})
	}
	return c.JSON(bootstrap)
}

func (h *Handler) acknowledgeModerationNotice(c *fiber.Ctx) error {
	userID, _ := c.Locals("user_id").(string)
	if err := h.service.AcknowledgeModerationNotice(userID, c.Params("id")); err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return c.Status(fiber.StatusNotFound).JSON(fiber.Map{"error": "avviso non trovato"})
		}
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "conferma non riuscita"})
	}
	return c.SendStatus(fiber.StatusNoContent)
}

func (h *Handler) acceptTerms(c *fiber.Ctx) error {
	var body struct {
		Version string `json:"version"`
		Adult   bool   `json:"adult"`
	}
	if err := c.BodyParser(&body); err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "richiesta non valida"})
	}
	if err := h.service.AcceptTerms(c.Locals("user_id").(string), body.Version, body.Adult); err != nil {
		if errors.Is(err, ErrTerms) {
			return c.Status(400).JSON(fiber.Map{"error": err.Error()})
		}
		return c.Status(500).JSON(fiber.Map{"error": "accettazione non riuscita, riprova"})
	}
	return c.SendStatus(fiber.StatusNoContent)
}
