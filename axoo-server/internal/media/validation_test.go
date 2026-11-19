package media

import (
	"axoo-server/internal/models"
	"testing"
)

func TestProviderHostsAndDeliveryParameters(t *testing.T) {
	for _, provider := range []string{"giphy", "klipy"} {
		for _, kind := range []string{"gif", "sticker"} {
			host := "media.giphy.com"
			if provider == "klipy" {
				host = "static.klipy.com"
			}
			media := models.ImageMeta{Type: kind, Provider: provider, ProviderID: "hello-friends",
				URL: "https://" + host + "/image.gif?delivery=original&token=keep", Width: 320, Height: 240}
			original := media.URL
			if err := ValidateRemoteAttachment(&media); err != nil {
				t.Fatal(err)
			}
			if media.URL != original {
				t.Fatal("delivery URL changed")
			}
		}
	}
}

func TestInvalidKlipyMedia(t *testing.T) {
	valid := models.ImageMeta{Type: "gif", Provider: "klipy", ProviderID: "hello", URL: "https://static.klipy.com/a.gif", Width: 100, Height: 100}
	for _, change := range []func(*models.ImageMeta){
		func(m *models.ImageMeta) { m.URL = "https://static.klipy.com.evil.example/a.gif" },
		func(m *models.ImageMeta) { m.URL = "http://static.klipy.com/a.gif" },
		func(m *models.ImageMeta) { m.URL = "https://evil.example@static.klipy.com/a.gif" },
		func(m *models.ImageMeta) { m.URL = "https://static.klipy.com:443/a.gif" },
		func(m *models.ImageMeta) { m.URL = "https://media.giphy.com/a.gif" },
		func(m *models.ImageMeta) { m.ThumbnailURL = "https://evil.example/a.gif" },
		func(m *models.ImageMeta) { m.PreviewFallbackURL = "https://evil.example/a.gif" },
		func(m *models.ImageMeta) { m.Provider = "giphy" },
		func(m *models.ImageMeta) { m.ProviderID = "" },
		func(m *models.ImageMeta) { m.Type = "photo" },
		func(m *models.ImageMeta) { m.Width = 0 },
	} {
		media := valid
		change(&media)
		if ValidateRemoteAttachment(&media) == nil {
			t.Fatalf("accepted invalid attachment: %+v", media)
		}
	}
}
