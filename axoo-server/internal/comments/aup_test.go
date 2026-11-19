package comments

import (
	"axoo-server/internal/models"
	"testing"
)

func TestAUPStickerCanBeAttached(t *testing.T) {
	media := &models.ImageMeta{Type: "sticker", Provider: "aup", ProviderID: "aup-12", MIMEType: "image/webp", Width: 512, Height: 512,
		URL: "https://cyrfznxopjeeidcmfgze.supabase.co/storage/v1/object/public/memes/stickers/aup/v1/12.webp"}
	if err := validateCommentMedia(media); err != nil {
		t.Fatal(err)
	}
}
