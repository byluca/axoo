// Package referrals keeps the retired invitation API compatible with older clients.
package referrals

import "github.com/gofiber/fiber/v2"

// The historical Content Desk metrics use the same reward pack and target.
const RewardPack = "amici-di-axoo"
const Target = 1

type reward struct {
	PackID     string `json:"pack_id"`
	Target     int    `json:"target"`
	EmojiCount int    `json:"emoji_count"`
	Unlocked   bool   `json:"unlocked"`
}

type status struct {
	Enabled       bool     `json:"enabled"`
	Code          string   `json:"code"`
	ValidInvites  int      `json:"valid_invites"`
	Target        int      `json:"target"`
	RewardPack    string   `json:"reward_pack"`
	CanClaim      bool     `json:"can_claim"`
	ShowPrompt    bool     `json:"show_prompt"`
	Claimed       bool     `json:"claimed"`
	UnlockedPacks []string `json:"unlocked_packs"`
	Rewards       []reward `json:"rewards"`
}

func RegisterRoutes(router fiber.Router) {
	group := router.Group("/users/me/referral")
	group.Get("", func(c *fiber.Ctx) error {
		c.Set("Cache-Control", "private, no-store")
		return c.JSON(status{
			Target:        Target,
			RewardPack:    RewardPack,
			UnlockedPacks: []string{RewardPack, "pack-16"},
			Rewards:       []reward{{RewardPack, Target, 112, true}, {"pack-16", 2, 50, true}},
		})
	})
	group.Post("/claim", func(c *fiber.Ctx) error {
		return c.Status(400).JSON(fiber.Map{"error": "tutti i pack sono gratuiti, gli inviti non sono più necessari"})
	})
	group.Post("/dismiss", func(c *fiber.Ctx) error { return c.SendStatus(204) })
}
