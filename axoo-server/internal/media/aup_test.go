package media

import (
	"axoo-server/internal/models"
	"testing"
)

func TestAUPAllowsOnlyTheKnownOriginalStickerFiles(t *testing.T) {
	original := models.ImageMeta{Type: "sticker", Provider: "aup", ProviderID: "aup-12", MIMEType: "image/webp", Width: 512, Height: 512,
		URL: "https://cyrfznxopjeeidcmfgze.supabase.co/storage/v1/object/public/memes/stickers/aup/v1/12.webp"}
	original.ThumbnailURL = original.URL
	if err := ValidateRemoteAttachment(&original); err != nil {
		t.Fatal(err)
	}
	legacy := original
	legacy.PreviewFallbackURL = "https://cyrfznxopjeeidcmfgze.supabase.co/storage/v1/object/public/memes/stickers/aup/v1/12.gif"
	if err := ValidateRemoteAttachment(&legacy); err != nil {
		t.Fatal(err)
	}
	for _, mutate := range []func(*models.ImageMeta){
		func(m *models.ImageMeta) { m.ProviderID = "aup-18" },
		func(m *models.ImageMeta) { m.ProviderID = "aup-012" },
		func(m *models.ImageMeta) { m.Type = "gif" },
		func(m *models.ImageMeta) { m.MIMEType = "image/gif" },
		func(m *models.ImageMeta) { m.Width = 0 },
		func(m *models.ImageMeta) { m.URL += "?other=true" },
		func(m *models.ImageMeta) { m.ThumbnailURL = "https://evil.test/a.webp" },
		func(m *models.ImageMeta) {
			m.URL = "https://cyrfznxopjeeidcmfgze.supabase.co/storage/v1/object/public/memes/other.webp"
		},
	} {
		invalid := original
		mutate(&invalid)
		if err := ValidateRemoteAttachment(&invalid); err == nil {
			t.Fatalf("invalid AUP attachment accepted: %+v", invalid)
		}
	}
}
