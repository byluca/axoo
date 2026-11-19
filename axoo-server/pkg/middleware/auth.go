package middleware

import (
	"encoding/json"
	"net/http"
	"strings"
	"time"

	"github.com/gofiber/fiber/v2"
)

type supabaseUser struct {
	ID    string `json:"id"`
	Email string `json:"email"`
}

// RequireAuth delega la verifica del Token direttamente a Supabase.
// Fabbrica e restituisce il middleware ("buttafuori") per proteggere l'Area VIP.
func RequireAuth(supabaseURL, supabaseKey string) fiber.Handler {
	endpoint := strings.TrimRight(supabaseURL, "/") + "/auth/v1/user"
	client := &http.Client{Timeout: 5 * time.Second}

	// Restituisce la funzione anonima (il buttafuori vero e proprio)
	return func(c *fiber.Ctx) error {
		// 1. Controlla che il token esista
		// HTTP/2 retains header strings after this handler; detach Fiber's reusable buffer.
		header := strings.Clone(c.Get("Authorization"))
		if !strings.HasPrefix(header, "Bearer ") {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{"error": "token mancante"})
		}
		if supabaseURL == "" || supabaseKey == "" {
			return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "autenticazione non configurata"})
		}

		// 2. Chiama Supabase per verificare se il token è autentico
		req, err := http.NewRequestWithContext(c.Context(), http.MethodGet, endpoint, nil)
		if err != nil {
			return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "errore autenticazione"})
		}
		req.Header.Set("apikey", supabaseKey)
		req.Header.Set("Authorization", header)
		resp, err := client.Do(req)
		if err != nil || resp.StatusCode != http.StatusOK {
			if resp != nil {
				resp.Body.Close()
			}
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{"error": "token non valido"})
		}
		defer resp.Body.Close()

		// 3. Legge la risposta per estrarre l'utente
		var user supabaseUser
		if err := json.NewDecoder(resp.Body).Decode(&user); err != nil || user.ID == "" || user.Email == "" {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{"error": "token non valido"})
		}

		// 4. Salva i dati utente nel Context per usarli nelle rotte successive
		c.Locals("user_id", user.ID)
		c.Locals("user_email", user.Email)

		// 5. Lascia passare l'utente (apre i tornelli)!
		return c.Next()
	}
}
