package models

import (
	"encoding/json"
	"testing"
)

func TestVoteRequestSupportsCurrentAndLegacyClients(t *testing.T) {
	for _, tc := range []struct {
		body  string
		value int
		valid bool
	}{
		{`{"value":0,"type":"upvote"}`, 0, true},
		{`{"value":-1}`, -1, true},
		{`{"value":1}`, 1, true},
		{`{"value":2,"type":"downvote"}`, 2, false},
		{`{"type":"upvote"}`, 1, true},
		{`{"type":"downvote"}`, -1, true},
		{`{}`, 0, false},
	} {
		t.Run(tc.body, func(t *testing.T) {
			var request VoteRequest
			if err := json.Unmarshal([]byte(tc.body), &request); err != nil {
				t.Fatal(err)
			}
			if value, valid := request.DesiredValue(); value != tc.value || valid != tc.valid {
				t.Fatalf("DesiredValue() = (%d, %t), want (%d, %t)", value, valid, tc.value, tc.valid)
			}
		})
	}
}
