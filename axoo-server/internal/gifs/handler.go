package gifs

import "github.com/gofiber/fiber/v2"

func RegisterRoutes(router fiber.Router, client *HTTPGiphyClient) {
	for _, collection := range []string{"gifs", "stickers"} {
		router.Get("/"+collection+"/trending", func(c *fiber.Ctx) error {
			result, err := client.fetch(collection, "trending", "", c.QueryInt("limit", 30), c.QueryInt("offset", 0))
			if err != nil {
				return c.Status(502).JSON(fiber.Map{"error": "errore chiamata Giphy"})
			}
			return c.JSON(result)
		})
		router.Get("/"+collection+"/search", func(c *fiber.Ctx) error {
			query := c.Query("q")
			if query == "" {
				return c.Status(400).JSON(fiber.Map{"error": "parametro q mancante"})
			}
			result, err := client.fetch(collection, "search", query, c.QueryInt("limit", 30), c.QueryInt("offset", 0))
			if err != nil {
				return c.Status(502).JSON(fiber.Map{"error": "errore parsing risposta Giphy"})
			}
			return c.JSON(result)
		})
	}
}
