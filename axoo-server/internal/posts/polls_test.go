package posts

import (
	"axoo-server/internal/models"
	"encoding/json"
	"strings"
	"testing"
)

func TestPollValidationAndEmojiPreservation(t *testing.T) {
	service := NewService(nil, nil, nil)
	valid := []models.PollOptionInput{{Text: "Caffè ☕", Entities: models.MessageEntitySlice{{Type: "static_emoji", Offset: 6, Length: 1}}}, {Text: "Tè"}}
	options, err := service.validatePoll("me", &models.PollInput{Options: valid})
	if err != nil {
		t.Fatal(err)
	}
	if len(options) != 2 || options[0].ID == options[1].ID || options[0].Entities[0] != valid[0].Entities[0] {
		t.Fatalf("lost option content: %+v", options)
	}
	for _, input := range [][]models.PollOptionInput{
		nil, valid[:1], append(append([]models.PollOptionInput{}, valid...), valid[0], valid[0], valid[0]),
		{{Text: " "}, {Text: "ok"}}, {{Text: strings.Repeat("😀", 101)}, {Text: "ok"}},
		{{Text: "x", Entities: models.MessageEntitySlice{{Type: "custom_emoji", Offset: 5, Length: 1}}}, {Text: "ok"}},
	} {
		if _, err := service.validatePoll("me", &models.PollInput{Options: input}); err == nil {
			t.Fatalf("accepted invalid input: %+v", input)
		}
	}
	if _, err := service.validatePoll("me", &models.PollInput{Options: []models.PollOptionInput{{Text: strings.Repeat("😀", 100)}, {Text: "ok"}}}); err != nil {
		t.Fatal(err)
	}
}

func TestPollIdempotencyIncludesTextEntitiesOrderAndPresence(t *testing.T) {
	service := NewService(nil, nil, nil)
	input := &models.PollInput{Options: []models.PollOptionInput{{Text: "A"}, {Text: "B"}}}
	a, _ := service.validatePoll("me", input)
	b, _ := service.validatePoll("me", input)
	if a[0].ID == b[0].ID || !samePollPayload(a, b) {
		t.Fatal("retries must compare content independently of generated ids")
	}
	if samePollPayload(a, nil) || samePollPayload(a, models.PollOptions{b[1], b[0]}) {
		t.Fatal("changed poll must conflict")
	}
	b[0].Entities = models.MessageEntitySlice{{Type: "static_emoji", Offset: 0, Length: 1}}
	if samePollPayload(a, b) {
		t.Fatal("emoji changes must conflict")
	}
}

func TestPollResponseAndStorageRoundTrip(t *testing.T) {
	poll := models.PostPoll{Options: []models.PostPollOption{{PollOptionDefinition: models.PollOptionDefinition{ID: "option", Text: "A", Entities: models.MessageEntitySlice{}}, Votes: 2, Voters: []models.PollVoter{{UserID: "voter", AvatarConfig: models.AvatarConfig{"seed": "test"}}}}}, TotalVotes: 2, MyOptionID: "option"}
	raw, err := json.Marshal(poll)
	if err != nil {
		t.Fatal(err)
	}
	var response map[string]any
	if err := json.Unmarshal(raw, &response); err != nil {
		t.Fatal(err)
	}
	option := response["options"].([]any)[0].(map[string]any)
	if option["id"] != "option" || option["text"] != "A" || option["votes"] != float64(2) {
		t.Fatalf("incorrect Android contract: %s", raw)
	}
	original := models.PollOptions{poll.Options[0].PollOptionDefinition}
	value, err := original.Value()
	if err != nil {
		t.Fatal(err)
	}
	var saved models.PollOptions
	if err := saved.Scan(value); err != nil || !samePollPayload(original, saved) {
		t.Fatalf("storage round trip failed: %+v %v", saved, err)
	}
}
