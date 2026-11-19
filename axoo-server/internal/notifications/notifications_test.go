package notifications

import (
	"encoding/json"
	"math"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"

	"axoo-server/internal/models"

	"github.com/gofiber/fiber/v2"
)

func TestPreviewPreservesEmojiIDsAndUnicodeOffsetsWithoutPartialEntities(t *testing.T) {
	preview := "😀\n" + strings.Repeat("a", 156) + "❤️"
	valid := models.MessageEntity{Type: "custom_emoji", Offset: 158, Length: 2, CustomEmojiID: 42}
	static := models.MessageEntity{Type: "static_emoji", Offset: 0, Length: 1, CustomEmojiID: 1}
	entities := models.MessageEntitySlice{
		valid, static,
		{Type: "custom_emoji", Offset: 159, Length: 2, CustomEmojiID: 43},
		{Type: "custom_emoji", Offset: 160, Length: 1, CustomEmojiID: 44},
		{Type: "custom_emoji", Offset: -1, Length: 1, CustomEmojiID: 45},
		{Type: "custom_emoji", Offset: 0, Length: math.MaxInt, CustomEmojiID: 46},
		{Type: "mention", Offset: 0, Length: 1},
	}
	got := trimPreviewEntities(preview, entities)
	if !reflect.DeepEqual(got, models.MessageEntitySlice{valid, static}) {
		t.Fatalf("wrong preview entities: %+v", got)
	}
	raw, err := json.Marshal(Notification{Preview: preview, PreviewEntities: got})
	if err != nil || !strings.Contains(string(raw), `"preview_entities":[`) || !strings.Contains(string(raw), `"custom_emoji_id":42`) {
		t.Fatalf("emoji metadata missing from response: %s, %v", raw, err)
	}
}

func TestPreviewWithoutEmojiReturnsAnEmptyArray(t *testing.T) {
	raw, err := json.Marshal(trimPreviewEntities("plain text", nil))
	if err != nil || string(raw) != "[]" {
		t.Fatalf("%s, %v", raw, err)
	}
}

func TestMessageUsesAuthorNickname(t *testing.T) {
	if Message("post_comment", " Giulia ") != "Giulia ha commentato il tuo post" || Message("comment_reply", "Marco") != "Marco ha risposto al tuo commento" {
		t.Fatal("notification does not name the author")
	}
}

func TestParseIDRejectsInvalidCursors(t *testing.T) {
	for _, value := range []string{"", "-1", "0", "1 OR 1=1", "9223372036854775808"} {
		if _, err := ParseID(value); err == nil {
			t.Fatalf("accepted %q", value)
		}
	}
	if id, err := ParseID("42"); err != nil || id != 42 {
		t.Fatal(id, err)
	}
}

type testStore struct{ user, cursor, through string }

func (s *testStore) List(user, cursor string, _ int) (*Page, error) {
	s.user, s.cursor = user, cursor
	return &Page{Notifications: []Notification{}}, nil
}
func (s *testStore) Unread(user string) (int64, error) { s.user = user; return 3, nil }
func (s *testStore) Seen(user, through string) (int64, error) {
	s.user, s.through = user, through
	return 1, nil
}

func TestRoutesAlwaysUseAuthenticatedRecipient(t *testing.T) {
	store := &testStore{}
	app := fiber.New()
	app.Use(func(c *fiber.Ctx) error { c.Locals("user_id", "me"); return c.Next() })
	RegisterRoutes(app, store)
	for _, path := range []string{"/notifications?recipient_id=other&cursor=42", "/notifications/unread-count?recipient_id=other"} {
		response, err := app.Test(httptest.NewRequest("GET", path, nil))
		if err != nil {
			t.Fatal(err)
		}
		response.Body.Close()
		if response.StatusCode != 200 || store.user != "me" {
			t.Fatal("wrong recipient", store.user)
		}
	}
	request := httptest.NewRequest("POST", "/notifications/seen", strings.NewReader(`{"seen_through":"42","recipient_id":"other"}`))
	request.Header.Set("Content-Type", "application/json")
	response, err := app.Test(request)
	if err != nil {
		t.Fatal(err)
	}
	response.Body.Close()
	if response.StatusCode != 200 || store.user != "me" || store.through != "42" {
		t.Fatal("wrong acknowledgement recipient")
	}
}
