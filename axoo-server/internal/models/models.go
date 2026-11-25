package models

import (
	"database/sql/driver"
	"encoding/json"
	"fmt"
	"time"
)

// ImageMeta contiene URL e dimensioni di un'immagine.
type ImageMeta struct {
	URL                string `json:"url"`
	ThumbnailURL       string `json:"thumbnail_url,omitempty"`
	Width              int    `json:"width"`
	Height             int    `json:"height"`
	Type               string `json:"type,omitempty"`
	MIMEType           string `json:"mime_type,omitempty"`
	PreviewFallbackURL string `json:"preview_fallback_url,omitempty"`
	Provider           string `json:"provider,omitempty"`
	ProviderID         string `json:"provider_id,omitempty"`
}

func (m ImageMeta) Value() (driver.Value, error) {
	b, err := json.Marshal(m)
	return string(b), err
}

func (m *ImageMeta) Scan(value any) error {
	if value == nil {
		*m = ImageMeta{}
		return nil
	}
	var raw []byte
	switch v := value.(type) {
	case string:
		raw = []byte(v)
	case []byte:
		raw = v
	default:
		return fmt.Errorf("tipo non supportato: %T", value)
	}
	return json.Unmarshal(raw, m)
}

// AvatarConfig rappresenta le scelte dell'utente per personalizzare il suo avatar DiceBear.
type AvatarConfig map[string]any

// converte i dati dal mondo Golang al mondo SQL
func (a AvatarConfig) Value() (driver.Value, error) {
	if a == nil {
		return nil, nil
	}
	b, err := json.Marshal(a)
	return string(b), err
}

// converte i dati dal mondo SQL al mondo Golang
func (a *AvatarConfig) Scan(value any) error {
	if value == nil {
		*a = nil
		return nil
	}
	var bytes []byte
	switch v := value.(type) {
	case string:
		bytes = []byte(v)
	case []byte:
		bytes = v
	default:
		return fmt.Errorf("tipo non supportato: %T", value)
	}
	return json.Unmarshal(bytes, a)
}

type User struct {
	IsProfilePublic bool               `gorm:"not null;default:false" json:"is_profile_public"`
	ID              string             `gorm:"primaryKey;type:uuid;default:gen_random_uuid()" json:"id"`
	EmailHash       string             `gorm:"uniqueIndex;not null"                           json:"-"`
	Nickname        string             `gorm:"uniqueIndex;not null"                           json:"nickname"`
	Bio             string             `gorm:"not null;default:''"                            json:"bio"`
	BioEntities     MessageEntitySlice `gorm:"type:jsonb;not null;default:'[]'" json:"bio_entities,omitempty"`
	UniversityID    string             `gorm:"not null"                                       json:"university_id"`
	IsAlphaTester   bool               `gorm:"column:is_alpha_tester;not null;default:false"    json:"-"`
	IsModerator     bool               `gorm:"column:is_moderator;not null;default:false"       json:"-"`
	IsAdmin         bool               `gorm:"column:is_admin;not null;default:false"           json:"-"`

	AvatarConfig     AvatarConfig `gorm:"type:jsonb"                                     json:"avatar_config"`
	SuspendedUntil   *time.Time   `gorm:"default:null"                                   json:"-"`
	SuspensionReason string       `gorm:"not null;default:''"                            json:"-"`
	TermsVersion     string       `gorm:"not null;default:''" json:"-"`
	TermsAcceptedAt  *time.Time   `json:"-"`
	IsBanned         bool         `gorm:"not null;default:false"                         json:"-"`
	CreatedAt        time.Time    `                                                      json:"created_at"`
	University       University   `gorm:"foreignKey:UniversityID;references:ID"           json:"-"`
}

// UserBlock is intentionally one-way: blocking is a personal feed preference
// and never notifies or changes the blocked account.
type UserBlock struct {
	BlockerID string    `gorm:"primaryKey;type:uuid" json:"-"`
	BlockedID string    `gorm:"primaryKey;type:uuid" json:"id"`
	CreatedAt time.Time `json:"created_at"`
}

func (UserBlock) TableName() string { return "user_blocks" }

// NewFeedState conserva l'ultimo momento in cui un utente ha visto il feed New della città.
type NewFeedState struct {
	UserID        string    `gorm:"primaryKey;type:uuid" json:"-"`
	NewFeedSeenAt time.Time `gorm:"not null"            json:"-"`
}

func (NewFeedState) TableName() string { return "user_feed_states" }

type University struct {
	ID        string    `gorm:"primaryKey;type:uuid"                  json:"id"`
	Name      string    `gorm:"not null"                              json:"name"`
	Domain    string    `gorm:"not null"                              json:"domain"`
	CityID    string    `gorm:"not null;type:uuid"                    json:"city_id"`
	City      City      `gorm:"foreignKey:CityID;references:ID"       json:"-"`
	CreatedAt time.Time `                                              json:"created_at"`
}

type City struct {
	ID        string    `gorm:"primaryKey;type:uuid" json:"id"`
	Name      string    `gorm:"uniqueIndex;not null" json:"name"`
	CreatedAt time.Time `                             json:"created_at"`
}

type Post struct {
	PollOptions    PollOptions        `gorm:"column:poll_options;type:jsonb" json:"-"`
	Poll           *PostPoll          `gorm:"-" json:"poll,omitempty"`
	ID             string             `gorm:"primaryKey;type:uuid;default:gen_random_uuid()" json:"id"`
	UserID         string             `gorm:"not null"                                       json:"user_id"`
	ClientID       *string            `gorm:"column:client_id;type:uuid"                     json:"-"`
	Nickname       string             `gorm:"-"                                              json:"nickname"`
	UniversityID   string             `gorm:"-"                                              json:"university_id"`
	UniversityName string             `gorm:"-"                                              json:"university_name"`
	CityID         string             `gorm:"not null;type:uuid"                             json:"city_id"`
	Body           string             `gorm:"not null"                                       json:"body"`
	Flair          string             `gorm:"default:null"                                   json:"flair"`
	Upvotes        int                `gorm:"->;-:migration"                                 json:"upvotes"`
	Downvotes      int                `gorm:"->;-:migration"                                 json:"downvotes"`
	CommentsCount  int                `gorm:"->;-:migration"                                 json:"comments_count"`
	Media          *ImageMeta         `gorm:"column:media;type:jsonb"                         json:"media,omitempty"`
	Entities       MessageEntitySlice `gorm:"column:entities;type:jsonb;default:'[]'"   json:"entities"`
	IsHidden       bool               `gorm:"default:false"                                  json:"-"`
	IsDeleted      bool               `gorm:"default:false"                                  json:"-"`
	CreatedAt      time.Time          `                                                      json:"created_at"`
	UpdatedAt      time.Time          `                                                      json:"updated_at"`
	MyVote         string             `gorm:"-"                                              json:"my_vote"`
	AvatarConfig   AvatarConfig       `gorm:"-"                                              json:"avatar_config"`
}

type PostVote struct {
	PostID    string `gorm:"primaryKey;type:uuid"`
	UserID    string `gorm:"primaryKey;type:uuid"`
	Type      string `gorm:"not null"` // "upvote" o "downvote"
	CreatedAt time.Time
}

type Comment struct {
	Media           *ImageMeta         `gorm:"column:media;type:jsonb" json:"media,omitempty"`
	ParentCommentID *string            `gorm:"column:parent_comment_id;type:uuid" json:"parent_comment_id,omitempty"`
	ID              string             `gorm:"primaryKey;type:uuid;default:gen_random_uuid()" json:"id"`
	PostID          string             `gorm:"not null"                                       json:"post_id"`
	UserID          string             `gorm:"not null"                                       json:"user_id"`
	Nickname        string             `gorm:"-"                                              json:"nickname"`
	UniversityName  string             `gorm:"-"                                              json:"university_name"`
	ClientID        *string            `gorm:"column:client_id;type:uuid"                     json:"client_id,omitempty"`
	Body            string             `gorm:"not null"                                       json:"body"`
	Upvotes         int                `gorm:"->;-:migration"                                 json:"upvotes"`
	Downvotes       int                `gorm:"->;-:migration"                                 json:"downvotes"`
	Entities        MessageEntitySlice `gorm:"column:entities;type:jsonb;default:'[]'"   json:"entities"`
	IsHidden        bool               `gorm:"default:false"                                  json:"-"`
	IsDeleted       bool               `gorm:"default:false"                                  json:"is_deleted"`
	AvatarConfig    AvatarConfig       `gorm:"-"                                              json:"avatar_config"`
	MyVote          string             `gorm:"-"                                              json:"my_vote"`
	CreatedAt       time.Time          `                                                      json:"created_at"`
	UpdatedAt       time.Time          `                                                      json:"updated_at"`
}

type Report struct {
	ID                 string  `gorm:"primaryKey;type:uuid;default:gen_random_uuid()"`
	ReporterID         string  `gorm:"not null"`
	TargetType         string  `gorm:"not null"` // "post" o "comment"
	TargetID           string  `gorm:"not null"`
	Reason             string  `gorm:"not null"`
	ModerationActionID *string `gorm:"type:uuid"`
	CreatedAt          time.Time
}

type ModerationAction struct {
	ID            string     `gorm:"primaryKey;type:uuid;default:gen_random_uuid()"`
	ModeratorID   *string    `gorm:"type:uuid"`
	Action        string     `gorm:"not null"`
	TargetType    string     `gorm:"not null"`
	TargetID      string     `gorm:"not null;type:uuid"`
	Reason        string     `gorm:"not null;default:''"`
	DurationHours *int       `gorm:"default:null"`
	AuthorSeenAt  *time.Time `gorm:"default:null"`
	CreatedAt     time.Time  `gorm:"not null;default:CURRENT_TIMESTAMP"`
}

// MessageEntity identifies an inline emoji inside a plain text body.
type MessageEntity struct {
	Type          string `json:"type"`              // "custom_emoji" or "static_emoji"
	Offset        int    `json:"offset"`            // Unicode code-point offset
	Length        int    `json:"length"`            // Unicode code-point length
	CustomEmojiID int    `json:"custom_emoji_id"`   // index into the emoji name list
	UserID        string `json:"user_id,omitempty"` // recipient for a mention entity
}

// MessageEntitySlice is a JSONB column storing []MessageEntity.
type MessageEntitySlice []MessageEntity

func (s MessageEntitySlice) Value() (driver.Value, error) {
	if s == nil {
		return "[]", nil
	}
	b, err := json.Marshal(s)
	return string(b), err
}

func (s *MessageEntitySlice) Scan(value any) error {
	var raw []byte
	switch v := value.(type) {
	case string:
		raw = []byte(v)
	case []byte:
		raw = v
	default:
		return fmt.Errorf("tipo non supportato: %T", value)
	}
	// Empty / null → empty slice
	if len(raw) == 0 || string(raw) == "null" {
		*s = nil
		return nil
	}
	return json.Unmarshal(raw, s)
}

type CommentVote struct {
	CommentID string `gorm:"primaryKey;type:uuid"`
	UserID    string `gorm:"primaryKey;type:uuid"`
	Type      string `gorm:"not null"` // "upvote" o "downvote"
	CreatedAt time.Time
}

// VoteRequest is shared by the post and comment vote endpoints.
type VoteRequest struct {
	Value *int   `json:"value"`
	Type  string `json:"type"` // Older clients use upvote or downvote.
}

func (r VoteRequest) DesiredValue() (int, bool) {
	if r.Value != nil {
		return *r.Value, *r.Value >= -1 && *r.Value <= 1
	}
	switch r.Type {
	case "upvote":
		return 1, true
	case "downvote":
		return -1, true
	default:
		return 0, false
	}
}

// VoteResult is the authoritative vote state returned after a write.
type VoteResult struct {
	Upvotes   int    `json:"upvotes"`
	Downvotes int    `json:"downvotes"`
	MyVote    string `json:"my_vote"`
	Changed   bool   `json:"-"`
}
