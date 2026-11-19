package posts

import (
	"axoo-server/internal/apierror"
	"encoding/json"
	"errors"
	"reflect"
	"strconv"
	"strings"
	"time"

	photomedia "axoo-server/internal/media"
	"axoo-server/internal/models"
	"axoo-server/internal/notifications"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

var ErrIdempotencyConflict = errors.New("client_id già usato per un altro post")

// PostRepository definisce le operazioni di persistenza per i post.
type PostRepository interface {
	LoadPolls(posts []models.Post, userID string) error
	SetPollVote(postID, userID, optionID string) (*models.PostPoll, error)
	HidePost(postID, userID string) error
	FindAll(cityID, userID string, f ListFilter) ([]models.Post, error)
	FindByID(id, userID string) (*models.Post, error)
	Create(post *models.Post) (bool, error)
	Update(postID, body, flair string, media *models.ImageMeta, entities []models.MessageEntity) error
	Delete(postID string) error
	SoftDelete(postID string) error
	FindOwner(postID string) (string, error)
	SetVote(postID, userID string, value int) (*models.VoteResult, error)
	NewFeedUnreadCount(cityID, userID string) (int, error)
	MarkNewFeedSeen(userID string, seenThrough time.Time) error
	CityIDForUser(userID string) (string, error)
}

// AvatarConfigLoader carica le avatar config per una lista di user ID.
type AvatarConfigLoader interface {
	LoadAvatarConfigs(userIDs []string) (map[string]models.AvatarConfig, error)
}

// ListFilter contiene i parametri di filtro e paginazione per la lista post.
type ListFilter struct {
	AuthorID string // Public profile: restrict posts to this author in the viewer city.
	Mine     bool   // Only posts belonging to the authenticated user, across cities.
	Order    string // "hot" | "new"
	Cursor   string
	Limit    int
	Offset   int
}

const DefaultLimit = 20

const postSelectWithCounts = `posts.*,
	(SELECT COUNT(*) FROM post_votes pv WHERE pv.post_id = posts.id AND pv.type = 'upvote') AS upvotes,
	(SELECT COUNT(*) FROM post_votes pv WHERE pv.post_id = posts.id AND pv.type = 'downvote') AS downvotes,
	(SELECT COUNT(*) FROM comments c WHERE c.post_id = posts.id AND c.is_deleted = FALSE AND c.is_hidden = FALSE) AS comments_count`

const postHotOrder = `(
	GREATEST(
		1 +
		(SELECT COUNT(*) FROM post_votes pv WHERE pv.post_id = posts.id AND pv.type = 'upvote') -
		(SELECT COUNT(*) FROM post_votes pv WHERE pv.post_id = posts.id AND pv.type = 'downvote') +
		2 * (SELECT COUNT(*) FROM comments c WHERE c.post_id = posts.id AND c.is_deleted = FALSE AND c.is_hidden = FALSE),
		0
	)::double precision /
	POWER(
		GREATEST(EXTRACT(EPOCH FROM (NOW() - posts.created_at)) / 3600.0, 0) + 2,
		1.5
	)::double precision
) DESC, posts.created_at DESC, posts.id DESC`

type Repository struct {
	db *gorm.DB
}

func NewRepository(db *gorm.DB) *Repository {
	return &Repository{db: db}
}

func (r *Repository) FindAll(cityID, userID string, f ListFilter) ([]models.Post, error) {
	if f.Limit <= 0 {
		f.Limit = DefaultLimit
	}
	if f.Order == "" {
		f.Order = "hot"
	}

	var posts []models.Post
	q := r.db.Model(&models.Post{}).Select(postSelectWithCounts).
		Where("posts.is_deleted = false AND posts.is_hidden = false")
	if f.Mine {
		q = q.Where("posts.user_id = ?", userID)
	} else {
		q = q.Where("posts.city_id = ?", cityID)
	}
	if f.AuthorID != "" {
		q = q.Where("posts.user_id = ?", f.AuthorID).
			Where(`EXISTS (SELECT 1 FROM users u JOIN universities uni ON uni.id = u.university_id
				WHERE u.id = posts.user_id AND uni.city_id = ? AND u.is_banned = false
				AND (u.is_profile_public = true OR u.id = ?)
				AND (u.suspended_until IS NULL OR u.suspended_until <= NOW()))`, cityID, userID)
	}
	q = excludeHiddenPosts(excludeBlockedAuthors(q, userID, "posts.user_id"), userID)

	var err error
	if f.Order == "new" {
		if f.Cursor != "" {
			parts := strings.SplitN(f.Cursor, "|", 2)
			if len(parts) == 2 {
				cursorTime, parseErr := time.Parse(time.RFC3339Nano, parts[0])
				if parseErr == nil {
					q = q.Where("created_at < ? OR (created_at = ? AND id < ?)", cursorTime, cursorTime, parts[1])
				}
			}
		}
		err = q.Order("posts.created_at DESC, posts.id DESC").Limit(f.Limit).Find(&posts).Error
	} else {
		offset := 0
		if f.Cursor != "" {
			if parsedOffset, err := strconv.Atoi(f.Cursor); err == nil {
				offset = parsedOffset
			}
		}
		err = q.Order(postHotOrder).
			Offset(offset).
			Limit(f.Limit).
			Find(&posts).Error
	}

	if err != nil {
		return nil, err
	}

	r.attachMyVotes(posts, userID)
	if err := r.LoadPolls(posts, userID); err != nil {
		return nil, err
	}

	return posts, nil
}

// NewFeedUnreadCount inizializza lo stato alla prima lettura, così l'attivazione
// della feature non trasforma tutti i post storici in notifiche non lette.
func (r *Repository) NewFeedUnreadCount(cityID, userID string) (int, error) {
	state := models.NewFeedState{
		UserID:        userID,
		NewFeedSeenAt: time.Now().UTC(),
	}
	if err := r.db.Clauses(clause.OnConflict{DoNothing: true}).Create(&state).Error; err != nil {
		return 0, err
	}

	var seenAt time.Time
	if err := r.db.Model(&models.NewFeedState{}).
		Select("new_feed_seen_at").
		Where("user_id = ?", userID).
		Scan(&seenAt).Error; err != nil {
		return 0, err
	}

	var count int64
	err := excludeHiddenPosts(excludeBlockedAuthors(r.db.Model(&models.Post{}), userID, "posts.user_id"), userID).
		Where("city_id = ?", cityID).
		Where("user_id <> ?", userID).
		Where("is_deleted = false AND (is_hidden = false OR is_hidden IS NULL)").
		Where("created_at > ?", seenAt).
		Count(&count).Error
	return int(count), err
}

func (r *Repository) MarkNewFeedSeen(userID string, seenThrough time.Time) error {
	state := models.NewFeedState{
		UserID:        userID,
		NewFeedSeenAt: seenThrough,
	}
	return r.db.Clauses(clause.OnConflict{
		Columns:   []clause.Column{{Name: "user_id"}},
		DoUpdates: clause.Assignments(map[string]interface{}{"new_feed_seen_at": gorm.Expr("GREATEST(user_feed_states.new_feed_seen_at, EXCLUDED.new_feed_seen_at)")}),
	}).Create(&state).Error
}

func (r *Repository) CityIDForUser(userID string) (string, error) {
	var row struct{ CityID string }
	err := r.db.Table("users AS u").
		Select("universities.city_id AS city_id").
		Joins("JOIN universities ON universities.id = u.university_id").
		Where("u.id = ?", userID).
		Take(&row).Error
	return row.CityID, err
}

func (r *Repository) fetchMyVote(postID, userID string) string {
	if userID == "" {
		return ""
	}
	var vote models.PostVote
	err := r.db.Select("type").Where("post_id = ? AND user_id = ?", postID, userID).First(&vote).Error
	if err != nil {
		return ""
	}
	return vote.Type
}

func (r *Repository) attachMyVotes(posts []models.Post, userID string) {
	if userID == "" || len(posts) == 0 {
		return
	}

	postIDs := make([]string, len(posts))
	for i, p := range posts {
		postIDs[i] = p.ID
	}

	var votes []models.PostVote
	r.db.Select("post_id, type").
		Where("post_id IN ? AND user_id = ?", postIDs, userID).
		Find(&votes)

	voteMap := make(map[string]string, len(votes))
	for _, v := range votes {
		voteMap[v.PostID] = v.Type
	}

	for i := range posts {
		posts[i].MyVote = voteMap[posts[i].ID]
	}
}

func (r *Repository) FindByID(id, userID string) (*models.Post, error) {
	var post models.Post
	err := excludeBlockedAuthors(r.db.Model(&models.Post{}).Select(postSelectWithCounts), userID, "posts.user_id").
		Where("posts.id = ? AND posts.is_deleted = false AND posts.is_hidden = false", id).First(&post).Error
	if err != nil {
		return nil, err
	}
	post.MyVote = r.fetchMyVote(id, userID)
	posts := []models.Post{post}
	if err := r.LoadPolls(posts, userID); err != nil {
		return nil, err
	}
	post = posts[0]
	return &post, nil
}

func excludeBlockedAuthors(db *gorm.DB, userID, authorColumn string) *gorm.DB {
	if userID == "" {
		return db
	}
	return db.Where(
		"NOT EXISTS (SELECT 1 FROM user_blocks WHERE blocker_id = ? AND blocked_id = "+authorColumn+")",
		userID,
	)
}

func (r *Repository) Create(post *models.Post) (created bool, err error) {
	err = r.db.Transaction(func(tx *gorm.DB) error {
		if post.ClientID != nil {
			lockKey := post.UserID + ":post:" + *post.ClientID
			if err := tx.Exec("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", lockKey).Error; err != nil {
				return err
			}
			var existing models.Post
			err := tx.Where("user_id = ? AND client_id = ?", post.UserID, *post.ClientID).First(&existing).Error
			if err == nil {
				requested := *post
				if !samePostPayload(existing, requested) {
					return ErrIdempotencyConflict
				}
				*post = existing
				return nil
			}
			if !errors.Is(err, gorm.ErrRecordNotFound) {
				return err
			}
		}

		if err := photomedia.ValidatePostPhoto(tx, post.UserID, post.Media); err != nil {
			return err
		}
		if err := tx.Create(post).Error; err != nil {
			return err
		}
		created = true
		return nil
	})
	return created, err
}

func samePostPayload(existing, requested models.Post) bool {
	return existing.Body == requested.Body &&
		existing.Flair == requested.Flair &&
		reflect.DeepEqual(existing.Media, requested.Media) &&
		reflect.DeepEqual(existing.Entities, requested.Entities) && samePollPayload(existing.PollOptions, requested.PollOptions)
}

func (r *Repository) Update(postID, body, flair string, media *models.ImageMeta, entities []models.MessageEntity) error {
	var mediaJSON any
	if media != nil {
		encoded, err := json.Marshal(media)
		if err != nil {
			return err
		}
		mediaJSON = string(encoded)
	}
	entityJSON, err := json.Marshal(entities)
	if err != nil {
		return err
	}
	return r.db.Transaction(func(tx *gorm.DB) error {
		var owner models.Post
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Select("user_id, poll_options").Where("id = ? AND is_deleted = false", postID).First(&owner).Error; err != nil {
			return err
		}
		if len(owner.PollOptions) > 0 {
			return apierror.Invalid(ErrPollImmutable)
		}
		if err := photomedia.ValidatePostPhoto(tx, owner.UserID, media); err != nil {
			return err
		}
		return tx.Exec(
			`UPDATE posts SET body = ?, flair = ?, media = ?::jsonb, entities = ?::jsonb, updated_at = NOW() WHERE id = ? AND is_deleted = false`,
			body, nullableFlair(flair), mediaJSON, string(entityJSON), postID,
		).Error
	})
}

func nullableFlair(flair string) any {
	if flair == "" {
		return nil
	}
	return flair
}

// Delete elimina un post. La cancellazione a cascata di commenti, voti, ecc.
// è delegata alle Foreign Keys del database (ON DELETE CASCADE).
func (r *Repository) Delete(postID string) error {
	return r.db.Where("id = ?", postID).Delete(&models.Post{}).Error
}

func (r *Repository) SoftDelete(postID string) error {
	return r.db.Model(&models.Post{}).Where("id = ? AND is_deleted = false", postID).Update("is_deleted", true).Error
}

func (r *Repository) FindOwner(postID string) (string, error) {
	var post models.Post
	err := r.db.Select("user_id").Where("id = ?", postID).First(&post).Error
	return post.UserID, err
}

func (r *Repository) SetVote(postID, userID string, value int) (*models.VoteResult, error) {
	var result models.VoteResult
	err := r.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.Exec("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", "post-vote:"+postID).Error; err != nil {
			return err
		}
		var post models.Post
		if err := excludeBlockedAuthors(tx.Model(&models.Post{}), userID, "posts.user_id").Select("id").Where("id = ? AND is_deleted = false AND is_hidden = false", postID).First(&post).Error; err != nil {
			return err
		}
		var existing models.PostVote
		err := tx.Where("post_id = ? AND user_id = ?", postID, userID).First(&existing).Error
		oldType := ""
		if err == nil {
			oldType = existing.Type
		} else if !errors.Is(err, gorm.ErrRecordNotFound) {
			return err
		}
		newType := voteType(value)
		changed := oldType != newType
		if oldType != newType {
			switch {
			case oldType == "" && newType != "":
				if err := tx.Create(&models.PostVote{PostID: postID, UserID: userID, Type: newType}).Error; err != nil {
					return err
				}
			case oldType != "" && newType == "":
				if err := tx.Delete(&existing).Error; err != nil {
					return err
				}
			default:
				if err := tx.Model(&existing).Update("type", newType).Error; err != nil {
					return err
				}
			}
		}
		if err := tx.Raw(`
			SELECT COUNT(*) FILTER (WHERE type = 'upvote') AS upvotes,
			       COUNT(*) FILTER (WHERE type = 'downvote') AS downvotes
			FROM post_votes WHERE post_id = ?
		`, postID).Scan(&result).Error; err != nil {
			return err
		}
		result.MyVote = newType
		result.Changed = changed
		if changed && newType == "upvote" {
			return notifications.RecordAppreciation(tx, postID, "", result.Upvotes)
		}
		return nil
	})
	return &result, err
}

func voteType(value int) string {
	if value == 1 {
		return "upvote"
	}
	if value == -1 {
		return "downvote"
	}
	return ""
}
