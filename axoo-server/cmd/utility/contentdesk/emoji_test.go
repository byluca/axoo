package main

import (
	"axoo-server/internal/models"
	"axoo-server/internal/textguard"
	"bytes"
	"io/fs"
	"net/http"
	"net/http/httptest"
	"os"
	"reflect"
	"strings"
	"testing"

	"golang.org/x/image/webp"
)

func TestEmojiExpansionPreservesUnicodeAndRepeatedPacks(t *testing.T) {
	catalog := &emojiCatalog{byName: map[string]deskEmoji{
		"amtab/heart-eyes": {ID: 12, Name: "amtab/heart-eyes", Fallback: "😍"},
		"spiral/heart":     {ID: 34, Name: "spiral/heart", Fallback: "❤️"},
	}}
	body, entities, err := catalog.expand("è 😀 :amtab/heart-eyes::spiral/heart:! :amtab/heart-eyes: fine")
	if err != nil {
		t.Fatal(err)
	}
	if body != "è 😀 😍❤️! 😍 fine" {
		t.Fatalf("unexpected body: %q", body)
	}
	want := []models.MessageEntity{
		{Type: "custom_emoji", Offset: 4, Length: 1, CustomEmojiID: 12},
		{Type: "custom_emoji", Offset: 5, Length: 2, CustomEmojiID: 34},
		{Type: "custom_emoji", Offset: 9, Length: 1, CustomEmojiID: 12},
	}
	if !reflect.DeepEqual(entities, want) {
		t.Fatalf("entities = %#v, want %#v", entities, want)
	}
	if err := textguard.ValidateBody("post", body, 500, 0, entities); err != nil {
		t.Fatal(err)
	}
}

func TestEmbeddedEmojiPreviews(t *testing.T) {
	paths, err := fs.Glob(emojiPreviewFiles, "emoji-previews/*/*.webp")
	if err != nil || len(paths) == 0 {
		t.Fatalf("missing embedded previews: %v", err)
	}
	for _, path := range paths {
		raw, err := emojiPreviewFiles.ReadFile(path)
		if err != nil {
			t.Fatal(err)
		}
		image, err := webp.Decode(bytes.NewReader(raw))
		if err != nil || image.Bounds().Dx() != 128 || image.Bounds().Dy() != 128 {
			t.Fatalf("invalid preview %s: %v", path, err)
		}
	}
	name := strings.TrimSuffix(strings.TrimPrefix(paths[0], "emoji-previews/"), ".webp")
	d := &desk{sessionToken: "test-token", emojis: &emojiCatalog{byName: map[string]deskEmoji{
		name: {Name: name},
	}}}
	handler := d.authorized(d.emojiPreview)
	for _, tc := range []struct {
		name, token string
		status      int
	}{
		{name, "test-token", http.StatusOK},
		{name, "", http.StatusUnauthorized},
		{name, "wrong-token", http.StatusUnauthorized},
		{"unknown/emoji", "test-token", http.StatusNotFound},
		{"../../catalog.json", "test-token", http.StatusNotFound},
	} {
		request := httptest.NewRequest("GET", "/api/emoji-preview?name="+tc.name, nil)
		request.AddCookie(&http.Cookie{Name: sessionCookie, Value: tc.token})
		response := httptest.NewRecorder()
		handler(response, request)
		if response.Code != tc.status {
			t.Fatalf("preview %q: status=%d, want %d", tc.name, response.Code, tc.status)
		}
		if tc.status == http.StatusOK {
			expected, _ := emojiPreviewFiles.ReadFile(paths[0])
			if response.Header().Get("Content-Type") != "image/webp" || !bytes.Equal(response.Body.Bytes(), expected) {
				t.Fatal("preview response differs from embedded WebP")
			}
		}
	}
}

func TestEmojiExpansionKeepsPlainTextAndRejectsUnknownCodes(t *testing.T) {
	catalog := &emojiCatalog{byName: map[string]deskEmoji{}}
	for _, source := range []string{"Ciao 😀", "https://example.com/a/b", "", " :amtab/heart-eyes"} {
		body, entities, err := catalog.expand(source)
		if err != nil || body != source || len(entities) != 0 {
			t.Fatalf("plain text changed: %q", source)
		}
	}
	if _, _, err := catalog.expand(":amtab/missing:"); err == nil {
		t.Fatal("unknown emoji accepted")
	}
}

func TestEmojiLimitAppliesToExpandedBody(t *testing.T) {
	catalog := &emojiCatalog{byName: map[string]deskEmoji{"amtab/heart-eyes": {ID: 12, Fallback: "😍"}}}
	for _, count := range []int{500, 501} {
		body, entities, err := catalog.expand(strings.Repeat(":amtab/heart-eyes:", count))
		if err != nil {
			t.Fatal(err)
		}
		err = textguard.ValidateBody("post", body, 500, 0, entities)
		if (err != nil) != (count > 500) {
			t.Fatalf("count=%d error=%v", count, err)
		}
	}
}

func TestAndroidEmojiCatalogAndAnimations(t *testing.T) {
	dir := "../../../../axoo-kotlin/app/src/main/assets/axooji"
	if _, err := os.Stat(dir); os.IsNotExist(err) {
		t.Skip("Android checkout not present")
	}
	catalog, err := loadEmojiCatalog(dir)
	if err != nil {
		t.Fatal(err)
	}
	for _, emoji := range catalog.Emojis {
		body, entities, err := catalog.expand(":" + emoji.Name + ":")
		if err != nil || body != emoji.Fallback || len(entities) != 1 || entities[0].CustomEmojiID != emoji.ID {
			t.Fatalf("catalog round trip failed for %s: %v", emoji.Name, err)
		}
	}
	t.Logf("Verified %d emoji IDs and animations from Android", len(catalog.Emojis))
}
