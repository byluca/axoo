package notifications

import (
	"errors"
	"fmt"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"axoo-server/internal/apierror"
	"axoo-server/internal/models"
	"github.com/gofiber/fiber/v2"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

type Notification struct {
	ID              int64                     `json:"id,string" gorm:"primaryKey;autoIncrement"`
	RecipientID     string                    `json:"-"`
	ActorID         string                    `json:"actor_id"`
	PostID          string                    `json:"post_id"`
	CommentID       string                    `json:"comment_id"`
	Kind            string                    `json:"kind"`
	Milestone       int                       `json:"-"`
	CreatedAt       time.Time                 `json:"created_at" gorm:"default:clock_timestamp()"`
	SeenAt          *time.Time                `json:"seen_at"`
	Nickname        string                    `json:"nickname" gorm:"->;-:migration"`
	Preview         string                    `json:"preview" gorm:"->;-:migration"`
	PreviewEntities models.MessageEntitySlice `json:"preview_entities" gorm:"->;-:migration"`
	Message         string                    `json:"message" gorm:"-"`
}

// Used for persistence, listing, counting and push delivery, including users without push enabled.
func EligibleComments(db *gorm.DB) *gorm.DB {
	return db.Joins("JOIN posts p ON p.id = c.post_id").
		Joins("LEFT JOIN comments parent ON parent.id = c.parent_comment_id AND parent.post_id = p.id").
		Joins("JOIN users u ON u.id = CASE WHEN c.parent_comment_id IS NULL THEN p.user_id ELSE parent.user_id END").
		Where("u.id <> c.user_id").
		Where("c.is_deleted = false AND c.is_hidden = false AND p.is_deleted = false AND p.is_hidden = false").
		Where("c.parent_comment_id IS NULL OR (parent.is_deleted = false AND parent.is_hidden = false)").
		Where("u.is_banned = false AND (u.suspended_until IS NULL OR u.suspended_until <= NOW())").
		Where("NOT EXISTS (SELECT 1 FROM user_blocks b WHERE b.blocker_id = u.id AND b.blocked_id IN (c.user_id, p.user_id))").
		Where("NOT EXISTS (SELECT 1 FROM user_hidden_posts h WHERE h.user_id = u.id AND h.post_id = p.id)")
}

// Must run in the transaction that creates the comment. An idempotent retry cannot duplicate it.
func RecordComment(tx *gorm.DB, commentID string) error {
	var rows []Notification
	err := EligibleComments(tx.Table("comments c")).Where("c.id = ?", commentID).
		Select("u.id AS recipient_id, c.user_id AS actor_id, c.post_id, c.id AS comment_id, CASE WHEN c.parent_comment_id IS NULL THEN 'post_comment' ELSE 'comment_reply' END AS kind").
		Scan(&rows).Error
	if err != nil || len(rows) == 0 {
		return err
	}
	return tx.Clauses(clause.OnConflict{Columns: []clause.Column{{Name: "recipient_id"}, {Name: "comment_id"}}, DoNothing: true}).Create(&rows).Error
}

func Message(kind, nickname string) string {
	nickname = strings.TrimSpace(nickname)
	if nickname == "" {
		nickname = "Anonimo"
	}
	if kind == "comment_reply" {
		return nickname + " ha risposto al tuo commento"
	}
	return nickname + " ha commentato il tuo post"
}

// RecordAppreciation is called only after a changed upvote, with the content vote
// lock held. It stores an aggregate milestone, never a voter's identity or a push.
func RecordAppreciation(tx *gorm.DB, postID, commentID string, upvotes int) error {
	kind := "post_appreciation"
	if commentID != "" {
		if upvotes != 5 {
			return nil
		}
		kind = "comment_appreciation"
	} else if upvotes != 10 && upvotes != 25 {
		return nil
	}
	q := tx.Table("posts p").Where("p.id = ? AND p.is_deleted = false AND p.is_hidden = false", postID)
	owner := "p.user_id"
	if commentID != "" {
		owner = "c.user_id"
		q = q.Joins("JOIN comments c ON c.post_id = p.id").
			Joins("LEFT JOIN comments parent ON parent.id = c.parent_comment_id AND parent.post_id = p.id").
			Where("c.id = ? AND c.is_deleted = false AND c.is_hidden = false", commentID).
			Where("c.parent_comment_id IS NULL OR (parent.id IS NOT NULL AND parent.is_deleted = false AND parent.is_hidden = false)")
	}
	var recipient string
	err := q.Joins("JOIN users u ON u.id = " + owner).
		Where("u.is_banned = false AND (u.suspended_until IS NULL OR u.suspended_until <= NOW())").
		Where("NOT EXISTS (SELECT 1 FROM user_blocks b WHERE b.blocker_id = u.id AND b.blocked_id = p.user_id)").
		Where("NOT EXISTS (SELECT 1 FROM user_hidden_posts h WHERE h.user_id = u.id AND h.post_id = p.id)").
		Select("u.id").Scan(&recipient).Error
	if err != nil || recipient == "" {
		return err
	}
	var target any
	if commentID != "" {
		target = commentID
	}
	return tx.Table("notifications").Clauses(clause.OnConflict{DoNothing: true}).Create(map[string]any{
		"recipient_id": recipient, "actor_id": recipient, "post_id": postID,
		"comment_id": target, "kind": kind, "milestone": upvotes,
	}).Error
}

func notificationMessage(n Notification) string {
	switch n.Kind {
	case "post_appreciation":
		return fmt.Sprintf("Il tuo post ha ricevuto %d voti positivi", n.Milestone)
	case "comment_appreciation":
		return fmt.Sprintf("Il tuo commento ha ricevuto %d voti positivi", n.Milestone)
	default:
		return Message(n.Kind, n.Nickname)
	}
}

type Page struct {
	Notifications []Notification `json:"notifications"`
	NextCursor    string         `json:"next_cursor"`
	SeenThrough   string         `json:"seen_through"`
	UnreadCount   int64          `json:"unread_count"`
}

type Repository struct{ db *gorm.DB }

func NewRepository(db *gorm.DB) *Repository { return &Repository{db: db} }

func (r *Repository) visible(userID string) *gorm.DB {
	return r.db.Table("notifications n").
		Joins("JOIN posts p ON p.id = n.post_id").
		Joins("LEFT JOIN comments c ON c.id = n.comment_id AND c.post_id = p.id").
		Joins("LEFT JOIN comments parent ON parent.id = c.parent_comment_id AND parent.post_id = p.id").
		Joins("JOIN users u ON u.id = n.recipient_id").
		Joins("JOIN users actor ON actor.id = n.actor_id").
		Where("n.recipient_id = ? AND p.is_deleted = false AND p.is_hidden = false", userID).
		Where("n.comment_id IS NULL OR (c.id IS NOT NULL AND c.is_deleted = false AND c.is_hidden = false AND (c.parent_comment_id IS NULL OR (parent.id IS NOT NULL AND parent.is_deleted = false AND parent.is_hidden = false)))").
		Where("u.is_banned = false AND (u.suspended_until IS NULL OR u.suspended_until <= NOW())").
		Where("NOT EXISTS (SELECT 1 FROM user_blocks b WHERE b.blocker_id = u.id AND b.blocked_id IN (n.actor_id, p.user_id))").
		Where("NOT EXISTS (SELECT 1 FROM user_hidden_posts h WHERE h.user_id = u.id AND h.post_id = p.id)").
		Where(`(n.kind IN ('post_comment', 'comment_reply') AND n.actor_id = c.user_id AND u.id <> c.user_id AND u.id = CASE WHEN c.parent_comment_id IS NULL THEN p.user_id ELSE parent.user_id END)
            OR (n.kind = 'post_appreciation' AND u.id = p.user_id AND n.actor_id = u.id)
            OR (n.kind = 'comment_appreciation' AND u.id = c.user_id AND n.actor_id = u.id)`)
}

func ParseID(value string) (int64, error) {
	id, err := strconv.ParseInt(value, 10, 64)
	if err != nil || id <= 0 {
		return 0, apierror.Invalid(errors.New("identificatore notifica non valido"))
	}
	return id, nil
}

func (r *Repository) List(userID, cursor string, limit int) (*Page, error) {
	if limit < 1 || limit > 50 {
		limit = 20
	}
	q := r.visible(userID).Select(`n.id, n.recipient_id, n.actor_id, n.post_id, COALESCE(n.comment_id::text, '') AS comment_id,
        n.kind, n.milestone, n.created_at, n.seen_at, actor.nickname,
        LEFT(CASE WHEN n.kind = 'post_appreciation' THEN p.body ELSE c.body END, 160) AS preview,
        CASE WHEN n.kind = 'post_appreciation' THEN p.entities ELSE c.entities END AS preview_entities`)
	if cursor != "" {
		id, err := ParseID(cursor)
		if err != nil {
			return nil, err
		}
		q = q.Where("n.id < ?", id)
	}
	rows := []Notification{}
	if err := q.Order("n.id DESC").Limit(limit + 1).Find(&rows).Error; err != nil {
		return nil, err
	}
	page := &Page{}
	if len(rows) > limit {
		rows = rows[:limit]
		page.NextCursor = strconv.FormatInt(rows[len(rows)-1].ID, 10)
	}
	for i := range rows {
		rows[i].Message = notificationMessage(rows[i])
		rows[i].PreviewEntities = trimPreviewEntities(rows[i].Preview, rows[i].PreviewEntities)
	}
	page.Notifications = rows
	if len(rows) > 0 {
		page.SeenThrough = strconv.FormatInt(rows[0].ID, 10)
	}
	count, err := r.Unread(userID)
	page.UnreadCount = count
	return page, err
}

func trimPreviewEntities(preview string, entities models.MessageEntitySlice) models.MessageEntitySlice {
	length := utf8.RuneCountInString(preview)
	result := make(models.MessageEntitySlice, 0, len(entities))
	for _, entity := range entities {
		if (entity.Type == "custom_emoji" || entity.Type == "static_emoji") &&
			entity.Offset >= 0 && entity.Offset <= length && entity.Length > 0 && entity.Length <= length-entity.Offset {
			result = append(result, entity)
		}
	}
	return result
}

func (r *Repository) Unread(userID string) (int64, error) {
	var count int64
	err := r.visible(userID).Where("n.seen_at IS NULL").Count(&count).Error
	return count, err
}

func (r *Repository) Seen(userID, through string) (int64, error) {
	id, err := ParseID(through)
	if err != nil {
		return 0, err
	}
	if err := r.db.Model(&Notification{}).Where("recipient_id = ? AND id <= ? AND seen_at IS NULL", userID, id).
		Update("seen_at", gorm.Expr("clock_timestamp()")).Error; err != nil {
		return 0, err
	}
	return r.Unread(userID)
}

type Store interface {
	List(userID, cursor string, limit int) (*Page, error)
	Unread(userID string) (int64, error)
	Seen(userID, through string) (int64, error)
}

func RegisterRoutes(router fiber.Router, store Store) {
	group := router.Group("/notifications")
	group.Get("/", func(c *fiber.Ctx) error {
		page, err := store.List(c.Locals("user_id").(string), c.Query("cursor"), c.QueryInt("limit", 20))
		if err != nil {
			return apierror.Respond(c, err)
		}
		return c.JSON(page)
	})
	group.Get("/unread-count", func(c *fiber.Ctx) error {
		count, err := store.Unread(c.Locals("user_id").(string))
		if err != nil {
			return apierror.Respond(c, err)
		}
		return c.JSON(fiber.Map{"unread_count": count})
	})
	group.Post("/seen", func(c *fiber.Ctx) error {
		var body struct {
			SeenThrough string `json:"seen_through"`
		}
		if err := c.BodyParser(&body); err != nil {
			return c.SendStatus(fiber.StatusBadRequest)
		}
		if _, err := ParseID(body.SeenThrough); err != nil {
			return apierror.Respond(c, err)
		}
		count, err := store.Seen(c.Locals("user_id").(string), body.SeenThrough)
		if err != nil {
			return apierror.Respond(c, err)
		}
		return c.JSON(fiber.Map{"unread_count": count})
	})
}
