// Package apierror separates invalid input from temporary infrastructure errors.
package apierror

import (
	"errors"
	"github.com/gofiber/fiber/v2"
	"gorm.io/gorm"
)

type validationError struct{ error }

func (e validationError) Unwrap() error { return e.error }

func Invalid(err error) error {
	if err == nil {
		return nil
	}
	return validationError{err}
}

var ErrForbidden = errors.New("non autorizzato")

func Respond(c *fiber.Ctx, err error) error {
	var invalid validationError
	switch {
	case errors.As(err, &invalid):
		return c.Status(400).JSON(fiber.Map{"error": err.Error()})
	case errors.Is(err, ErrForbidden):
		return c.Status(403).JSON(fiber.Map{"error": "non autorizzato"})
	case errors.Is(err, gorm.ErrRecordNotFound):
		return c.Status(404).JSON(fiber.Map{"error": "contenuto non disponibile"})
	default:
		return c.Status(500).JSON(fiber.Map{"error": "errore temporaneo, riprova"})
	}
}
