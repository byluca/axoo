package posts

import (
	"axoo-server/internal/models"
	"testing"
)

func TestAUPStickerCanBeAttached(t *testing.T) {
	media := &models.ImageMeta{Type: "sticker", Provider: "aup", ProviderID: "aup-12", MIMEType: "image/webp", Width: 512, Height: 512,
		URL: "https://cyrfznxopjeeidcmfgze.supabase.co/storage/v1/object/public/memes/stickers/aup/v1/12.webp"}
	normalized, err := normalizeAndValidateMedia(media)
	if err != nil {
		t.Fatal(err)
	}
	if *normalized != *media {
		t.Fatal("original sticker metadata changed")
	}
}
