package media

import (
	"errors"
	"net/url"
	"strconv"
	"strings"

	"axoo-server/internal/models"
)

// ValidateRemoteAttachment checks the provider against its own HTTPS delivery hosts.
// URLs, including query parameters, are preserved exactly as supplied by the provider.
func ValidateRemoteAttachment(media *models.ImageMeta) error {
	if (media.Type != "gif" && media.Type != "sticker" && media.Type != "meme") || strings.TrimSpace(media.ProviderID) == "" {
		return errors.New("allegato non valido")
	}
	if media.Provider == "axoo" {
		return validatePersonalSticker(media)
	}
	if media.Provider == "aup" {
		number, err := strconv.Atoi(strings.TrimPrefix(media.ProviderID, "aup-"))
		if err != nil || number < 1 || number > 17 || media.ProviderID != "aup-"+strconv.Itoa(number) ||
			media.Type != "sticker" || media.MIMEType != "image/webp" || media.Width != 512 || media.Height != 512 {
			return errors.New("sticker AUP non valido")
		}
		expected := "https://cyrfznxopjeeidcmfgze.supabase.co/storage/v1/object/public/memes/stickers/aup/v1/" + strconv.Itoa(number) + ".webp"
		fallback := expected
		if number == 12 {
			fallback = strings.TrimSuffix(expected, ".webp") + ".gif"
		}
		if media.URL != expected || (media.ThumbnailURL != "" && media.ThumbnailURL != expected) ||
			(media.PreviewFallbackURL != "" && media.PreviewFallbackURL != fallback) {
			return errors.New("URL sticker AUP non valido")
		}
		return nil
	}
	if media.Provider != "giphy" && media.Provider != "klipy" {
		return errors.New("provider media non valido")
	}
	if media.Type == "meme" && (media.Provider != "klipy" || (media.MIMEType != "image/png" && media.MIMEType != "image/jpeg" && media.MIMEType != "image/webp")) {
		return errors.New("meme non valido")
	}
	if media.Width < 1 || media.Height < 1 || media.Width > 8192 || media.Height > 8192 {
		return errors.New("dimensioni media non valide")
	}
	if media.URL == "" {
		return errors.New("URL media obbligatorio")
	}
	for _, raw := range []string{media.URL, media.ThumbnailURL, media.PreviewFallbackURL} {
		if raw == "" {
			continue
		}
		u, err := url.ParseRequestURI(raw)
		if err != nil || u.Scheme != "https" || u.User != nil || u.Port() != "" || len(raw) > 8192 || !allowedHost(media.Provider, u.Hostname()) {
			return errors.New("URL media non valido")
		}
	}
	return nil
}

func allowedHost(provider, host string) bool {
	if provider == "giphy" {
		return host == "giphy.com" || strings.HasSuffix(host, ".giphy.com")
	}
	return host == "static.klipy.com" || host == "static1.klipy.com" || host == "static2.klipy.com"
}
