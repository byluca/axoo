package users

import (
	"encoding/json"
	"strings"
	"testing"

	"axoo-server/internal/models"
)

func TestNormalizeBio(t *testing.T) {
	for _, bio := range []string{"", "  \n ", " studio informatica 🐱 \n", strings.Repeat("😀", 200)} {
		got, err := normalizeBio(bio)
		if err != nil || got != strings.TrimSpace(bio) {
			t.Fatalf("normalizeBio(%q) = %q, %v", bio, got, err)
		}
	}
	for _, bio := range []string{strings.Repeat("😀", 201), "bio\x00", string([]byte{0xff})} {
		if _, err := normalizeBio(bio); err != ErrInvalidBio {
			t.Fatalf("invalid bio was accepted: %v", err)
		}
	}
}

func TestPublicProfileContainsOnlyPublicFields(t *testing.T) {
	encoded, err := json.Marshal(PublicProfile{ID: "id", Nickname: "student", Bio: "bio", AvatarConfig: models.AvatarConfig{"seed": "cat"}})
	if err != nil {
		t.Fatal(err)
	}
	var fields map[string]any
	if err := json.Unmarshal(encoded, &fields); err != nil {
		t.Fatal(err)
	}
	publicFields := []string{"is_profile_public", "id", "nickname", "bio", "bio_entities", "university_id", "university_name", "avatar_config"}
	if len(fields) != len(publicFields) || fields["nickname"] != "student" || fields["bio"] != "bio" {
		t.Fatalf("unexpected public fields: %s", encoded)
	}
	for _, field := range publicFields {
		if _, found := fields[field]; !found {
			t.Fatalf("missing public field: %s", field)
		}
	}
}

func TestBioEmojiValidationAndTrim(t *testing.T) {
	entity := models.MessageEntity{Type: "custom_emoji", Offset: 2, Length: 1, CustomEmojiID: 42}
	got, err := normalizeBioEntities("  🐱 ciao  ", "🐱 ciao", []models.MessageEntity{entity})
	if err != nil || len(got) != 1 || got[0].Offset != 0 || got[0].Length != 1 {
		t.Fatalf("trimmed entities: %+v %v", got, err)
	}
	for _, entities := range [][]models.MessageEntity{
		{{Type: "mention", Offset: 0, Length: 1}},
		{{Type: "custom_emoji", Offset: -1, Length: 1}},
		{{Type: "custom_emoji", Offset: 0, Length: 0}},
		{{Type: "custom_emoji", Offset: 0, Length: 2}},
		{{Type: "custom_emoji", Offset: 0, Length: 1, CustomEmojiID: -1}},
		{{Type: "custom_emoji", Offset: 0, Length: 1, UserID: "other"}},
		{{Type: "custom_emoji", Offset: 0, Length: 1}, {Type: "custom_emoji", Offset: 0, Length: 1}},
		make([]models.MessageEntity, 21),
	} {
		if _, err := normalizeBioEntities("🐱", "🐱", entities); err != ErrInvalidBioEntities {
			t.Fatalf("invalid entities accepted: %+v", entities)
		}
	}
	if empty, err := normalizeBioEntities("", "", nil); err != nil || empty == nil || len(empty) != 0 {
		t.Fatalf("empty bio entities: %v %v", empty, err)
	}
}
