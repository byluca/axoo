package posts

import (
	"axoo-server/internal/models"
	"testing"
)

func TestKlipyStaticMemeKeepsItsOriginal(t *testing.T) {
	for _, mime := range []string{"image/png", "image/jpeg", "image/webp"} {
		m := &models.ImageMeta{Type: "meme", Provider: "klipy", ProviderID: "meme", MIMEType: mime,
			URL: "https://static.klipy.com/original.png?keep=true", ThumbnailURL: "https://static.klipy.com/small.webp", Width: 320, Height: 240}
		got, err := normalizeAndValidateMedia(m)
		if err != nil {
			t.Fatal(err)
		}
		if *got != *m {
			t.Fatal("meme metadata changed")
		}
		m.MIMEType = "video/mp4"
		if _, err := normalizeAndValidateMedia(m); err == nil {
			t.Fatal("video accepted as meme")
		}
	}
}
