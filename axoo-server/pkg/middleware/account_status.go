package middleware

import (
	"strings"
	"time"

	"axoo-server/internal/auth"
	"axoo-server/internal/models"

	"github.com/gofiber/fiber/v2"
	"gorm.io/gorm"
)

// RequireActiveUser protects normal app routes while allowing the authenticated
// session bootstrap to describe a suspended or banned account to the client.
func RequireActiveUser(db *gorm.DB) fiber.Handler {
	return func(c *fiber.Ctx) error {
		userID, _ := c.Locals("user_id").(string)
		if userID == "" {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{"error": "sessione non valida"})
		}
		// Il bootstrap deve poter leggere e restituire lo stato dell'account anche
		// quando l'utente è sospeso o bannato. La cancellazione resta disponibile
		// per consentire a chiunque di eliminare definitivamente il proprio account.
		if canProceedWithoutActiveUser(c.Method(), c.Path()) {
			return c.Next()
		}

		var user models.User
		err := db.Select("email_hash", "is_alpha_tester", "is_banned", "suspended_until", "suspension_reason", "terms_version", "terms_accepted_at").
			Where("id = ?", userID).
			First(&user).Error
		if err != nil {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{"error": "profilo non trovato"})
		}
		if user.IsAlphaTester {
			var eligible int64
			err = db.Table("alpha_testers").
				Where("email_hash = ?", user.EmailHash).
				Where("active = TRUE").
				Where("expires_at IS NULL OR expires_at > NOW()").
				Count(&eligible).Error
			if err != nil || eligible == 0 {
				return c.Status(fiber.StatusForbidden).JSON(fiber.Map{"error": "accesso alpha scaduto o revocato"})
			}
		}

		if user.IsBanned {
			return c.Status(fiber.StatusForbidden).JSON(fiber.Map{
				"error":  "account sospeso definitivamente",
				"code":   "account_banned",
				"reason": user.SuspensionReason,
			})
		}
		if user.SuspendedUntil != nil && user.SuspendedUntil.After(time.Now().UTC()) {
			return c.Status(fiber.StatusForbidden).JSON(fiber.Map{
				"error":           "account temporaneamente sospeso",
				"code":            "account_suspended",
				"reason":          user.SuspensionReason,
				"suspended_until": user.SuspendedUntil,
			})
		}

		if requiresTerms(c.Method(), c.Path()) && (user.TermsVersion != auth.TermsVersion || user.TermsAcceptedAt == nil) {
			return c.Status(fiber.StatusForbidden).JSON(fiber.Map{"error": "Accetta i Termini d’uso per continuare.", "code": "terms_required"})
		}
		return c.Next()
	}
}

// Reading one's profile, reporting, blocking and deleting remain available.
func requiresTerms(method, path string) bool {
	if method == fiber.MethodGet || method == fiber.MethodDelete {
		return false
	}
	if strings.HasSuffix(path, "/users/me/terms") {
		return false
	}
	return strings.Contains(path, "/posts") || strings.HasSuffix(path, "/users/me/nickname") || strings.HasSuffix(path, "/users/me/bio") || strings.HasSuffix(path, "/media/photos")
}

func canProceedWithoutActiveUser(method, path string) bool {
	return strings.HasSuffix(path, "/auth/session") ||
		(method == fiber.MethodDelete && (strings.HasSuffix(path, "/users/me") || strings.Contains(path, "/users/me/push-installations/")))
}
