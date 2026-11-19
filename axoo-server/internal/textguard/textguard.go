package textguard

import (
	"axoo-server/internal/models"
	"fmt"
	"unicode/utf8"
)

func ValidateBody(label, body string, maxRunes, maxEmojiEntities int, entities []models.MessageEntity) error {
	count := utf8.RuneCountInString(body)
	if count < 1 {
		return fmt.Errorf("%s non può essere vuoto", label)
	}
	if count > maxRunes {
		return fmt.Errorf("%s non può superare %d caratteri", label, maxRunes)
	}
	if maxEmojiEntities > 0 && len(entities) > maxEmojiEntities {
		return fmt.Errorf("%s non può contenere più di %d emoji animate", label, maxEmojiEntities)
	}
	textLen := len([]rune(body))
	for _, entity := range entities {
		if (entity.Type == "custom_emoji" || entity.Type == "static_emoji") && entity.CustomEmojiID >= 63000 && entity.CustomEmojiID <= 63045 {
			return fmt.Errorf("il pack Lungomare non è più disponibile")
		}
		if entity.Type != "custom_emoji" && entity.Type != "static_emoji" && entity.Type != "mention" {
			return fmt.Errorf("tipo entità non valido: %s", entity.Type)
		}
		if entity.Length <= 0 {
			return fmt.Errorf("lunghezza entità non valida")
		}
		if entity.Offset < 0 || entity.Offset+entity.Length > textLen {
			return fmt.Errorf("offset entità non valido")
		}
	}
	return nil
}
