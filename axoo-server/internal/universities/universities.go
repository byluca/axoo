package universities

import (
	"errors"
	"net/mail"
	"strings"
)

var ErrUnsupportedStudentEmail = errors.New("usa un'email istituzionale UniBa, PoliBa, LUM o Accademia di Bari")

// NormalizeStudentEmail returns the canonical email and its domain. The
// database decides whether that domain belongs to a supported university.
func NormalizeStudentEmail(raw string) (string, string, error) {
	email := strings.ToLower(strings.TrimSpace(raw))
	parsed, err := mail.ParseAddress(email)
	if err != nil || parsed.Address != email {
		return "", "", ErrUnsupportedStudentEmail
	}
	parts := strings.Split(email, "@")
	if len(parts) != 2 || parts[0] == "" || parts[1] == "" {
		return "", "", ErrUnsupportedStudentEmail
	}
	return email, parts[1], nil
}
