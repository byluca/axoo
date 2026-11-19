package models

import (
	"database/sql/driver"
	"encoding/json"
	"fmt"
	"time"
)

type PollOptionInput struct {
	Text     string             `json:"text"`
	Entities MessageEntitySlice `json:"entities"`
}

type PollInput struct {
	Options []PollOptionInput `json:"options"`
}

// Only immutable option definitions are stored on the post. Counts are queried.
type PollOptionDefinition struct {
	ID       string             `json:"id"`
	Text     string             `json:"text"`
	Entities MessageEntitySlice `json:"entities"`
}
type PollOptions []PollOptionDefinition

func (p PollOptions) Value() (driver.Value, error) {
	if len(p) == 0 {
		return nil, nil
	}
	raw, err := json.Marshal(p)
	return string(raw), err
}
func (p *PollOptions) Scan(value any) error {
	if value == nil {
		*p = nil
		return nil
	}
	var raw []byte
	switch v := value.(type) {
	case string:
		raw = []byte(v)
	case []byte:
		raw = v
	default:
		return fmt.Errorf("unsupported poll value: %T", value)
	}
	return json.Unmarshal(raw, p)
}

type PostPoll struct {
	Options    []PostPollOption `json:"options"`
	TotalVotes int              `json:"total_votes"`
	MyOptionID string           `json:"my_option_id,omitempty"`
}
type PostPollOption struct {
	PollOptionDefinition
	Votes  int         `json:"votes"`
	Voters []PollVoter `json:"voters"`
}
type PollVoter struct {
	UserID       string       `json:"user_id"`
	AvatarConfig AvatarConfig `json:"avatar_config"`
}
type PollVote struct {
	PostID    string `gorm:"primaryKey;type:uuid"`
	UserID    string `gorm:"primaryKey;type:uuid"`
	OptionID  string `gorm:"type:uuid;not null"`
	CreatedAt time.Time
}
