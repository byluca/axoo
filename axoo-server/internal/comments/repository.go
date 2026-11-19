package comments

import (
	"axoo-server/internal/notifications"
	"errors"
	"fmt"
	"reflect"

	"axoo-server/internal/models"

	"gorm.io/gorm"
)

//IL REPOSITORY è il Magazziniere (Data Access Layer).
// È "ignorante" e fa il lavoro sporco: si limita esclusivamente a interrogare il database
// per prendere, salvare o cancellare i dati grezzi. Non prende nessuna decisione aziendale.

var ErrIdempotencyConflict = errors.New("client_id già usato per un altro commento")

type CommentRepository interface {
	FindByPostID(postID, userID string) ([]models.Comment, error)
	FindByID(commentID string) (*models.Comment, error)
	FindOwner(commentID string) (string, error)
	FindReplyRecipient(commentID, postID string) (string, error)
	FindPostOwner(postID string) (string, error)
	Create(comment *models.Comment) (bool, error)
	Update(commentID, body string, entities []models.MessageEntity) error
	SoftDelete(commentID string) error
	SetVote(commentID, userID string, value int) (*models.VoteResult, error)
}

type Repository struct{ db *gorm.DB }

const commentSelectWithCounts = `comments.*,
	(SELECT COUNT(*) FROM comment_votes cv WHERE cv.comment_id = comments.id AND cv.type = 'upvote') AS upvotes,
	(SELECT COUNT(*) FROM comment_votes cv WHERE cv.comment_id = comments.id AND cv.type = 'downvote') AS downvotes`

const commentHotOrder = `(
	(SELECT COUNT(*) FROM comment_votes cv WHERE cv.comment_id = comments.id AND cv.type = 'upvote') -
	(SELECT COUNT(*) FROM comment_votes cv WHERE cv.comment_id = comments.id AND cv.type = 'downvote')
)::float / POWER(EXTRACT(EPOCH FROM (NOW() - comments.created_at)) / 3600 + 2, 1.5) DESC,
comments.created_at DESC, comments.id DESC`

// costruttore della struct Repository
func NewRepository(db *gorm.DB) *Repository { return &Repository{db: db} }

func (r *Repository) FindByPostID(postID, userID string) ([]models.Comment, error) {
	if err := visiblePost(r.db, postID, userID); err != nil {
		return nil, err
	}
	var rows []struct {
		models.Comment
		Unavailable bool
	}
	selection := commentSelectWithCounts + `, (comments.is_deleted OR comments.is_hidden`
	var args []any
	if userID != "" {
		selection += ` OR EXISTS (SELECT 1 FROM user_blocks WHERE blocker_id = ? AND blocked_id = comments.user_id)`
		args = append(args, userID)
	}
	q := r.db.Model(&models.Comment{}).Select(selection+`) AS unavailable`, args...).Where("comments.post_id = ?", postID)
	q = q.Order(commentHotOrder)
	if err := q.Find(&rows).Error; err != nil {
		return nil, err
	}
	comments := make([]models.Comment, 0, len(rows))
	for _, row := range rows {
		comment := row.Comment
		if row.Unavailable {
			comment = commentTombstone(comment)
		}
		comments = append(comments, comment)
	}
	comments = retainThreadAncestors(comments)
	if err := r.attachAuthorMetadata(r.db, comments); err != nil {
		return nil, err
	}
	r.attachMyVotes(comments, userID)
	return comments, nil
}

func (r *Repository) attachAuthorMetadata(db *gorm.DB, comments []models.Comment) error {
	if len(comments) == 0 {
		return nil
	}
	seen := make(map[string]bool)
	userIDs := make([]string, 0, len(comments))
	for _, comment := range comments {
		if comment.IsDeleted {
			continue
		}
		if !seen[comment.UserID] {
			seen[comment.UserID] = true
			userIDs = append(userIDs, comment.UserID)
		}
	}
	var users []models.User
	if err := db.Preload("University").Select("id, nickname, avatar_config, university_id").Where("id IN ?", userIDs).Find(&users).Error; err != nil {
		return err
	}
	byID := make(map[string]models.User, len(users))
	for _, user := range users {
		byID[user.ID] = user
	}
	for i := range comments {
		if comments[i].IsDeleted {
			continue
		}
		user, ok := byID[comments[i].UserID]
		if !ok || user.Nickname == "" {
			return fmt.Errorf("missing nickname for comment author %s", comments[i].UserID)
		}
		comments[i].Nickname = user.Nickname
		comments[i].AvatarConfig = user.AvatarConfig
		comments[i].UniversityName = user.University.Name
	}
	return nil
}

func (r *Repository) attachMyVotes(comments []models.Comment, userID string) {
	if userID == "" || len(comments) == 0 {
		return
	}
	ids := make([]string, len(comments))
	for i := range comments {
		ids[i] = comments[i].ID
	}
	var votes []models.CommentVote
	if err := r.db.Select("comment_id, type").Where("comment_id IN ? AND user_id = ?", ids, userID).Find(&votes).Error; err != nil {
		return
	}
	byComment := make(map[string]string, len(votes))
	for _, vote := range votes {
		byComment[vote.CommentID] = vote.Type
	}
	for i := range comments {
		if comments[i].IsDeleted {
			continue
		}
		comments[i].MyVote = byComment[comments[i].ID]
	}
}

func (r *Repository) FindOwner(commentID string) (string, error) {
	var comment models.Comment
	err := r.db.Select("user_id").Where("id = ?", commentID).First(&comment).Error
	return comment.UserID, err
}

func (r *Repository) FindByID(commentID string) (*models.Comment, error) {
	var comment models.Comment
	err := r.db.Select("id, post_id, user_id, body, entities, media, parent_comment_id").
		Where("id = ? AND is_deleted = false", commentID).
		First(&comment).Error
	return &comment, err
}

func (r *Repository) FindReplyRecipient(commentID, postID string) (string, error) {
	var comment models.Comment
	err := r.db.Select("user_id").
		Where("id = ? AND post_id = ? AND is_deleted = false AND is_hidden = false", commentID, postID).
		First(&comment).Error
	return comment.UserID, err
}

func (r *Repository) FindPostOwner(postID string) (string, error) {
	var post models.Post
	err := r.db.Select("user_id").Where("id = ?", postID).First(&post).Error
	return post.UserID, err
}

func (r *Repository) Create(comment *models.Comment) (created bool, err error) {
	err = r.db.Transaction(func(tx *gorm.DB) error {
		if err := visiblePost(tx, comment.PostID, comment.UserID); err != nil {
			return err
		}
		if comment.ClientID != nil {
			lockKey := comment.UserID + ":comment:" + *comment.ClientID
			if err := tx.Exec("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", lockKey).Error; err != nil {
				return err
			}
			var existing models.Comment
			err := tx.Where("user_id = ? AND client_id = ?", comment.UserID, *comment.ClientID).First(&existing).Error
			if err == nil {
				if !sameCommentRequest(existing, *comment) {
					return ErrIdempotencyConflict
				}
				*comment = existing
				one := []models.Comment{*comment}
				if err := r.attachAuthorMetadata(tx, one); err != nil {
					return err
				}
				*comment = one[0]
				return nil
			}
			if !errors.Is(err, gorm.ErrRecordNotFound) {
				return err
			}
		}

		if comment.ParentCommentID != nil {
			var parent models.Comment
			if err := tx.Select("id").Where("id = ? AND post_id = ? AND is_deleted = false AND is_hidden = false", *comment.ParentCommentID, comment.PostID).
				Where("NOT EXISTS (SELECT 1 FROM user_blocks WHERE blocker_id = ? AND blocked_id = comments.user_id)", comment.UserID).
				First(&parent).Error; err != nil {
				return err
			}
		}
		if err := tx.Create(comment).Error; err != nil {
			return err
		}
		var postCount int64
		if err := tx.Model(&models.Post{}).Where("id = ? AND is_deleted = false AND is_hidden = false", comment.PostID).Count(&postCount).Error; err != nil {
			return err
		}
		if postCount != 1 {
			return gorm.ErrRecordNotFound
		}
		one := []models.Comment{*comment}
		if err := r.attachAuthorMetadata(tx, one); err != nil {
			return err
		}
		*comment = one[0]
		if err := notifications.RecordComment(tx, comment.ID); err != nil {
			return err
		}
		created = true
		return nil
	})
	return created, err
}

func sameCommentRequest(a, b models.Comment) bool {
	sameParent := a.ParentCommentID == nil && b.ParentCommentID == nil ||
		a.ParentCommentID != nil && b.ParentCommentID != nil && *a.ParentCommentID == *b.ParentCommentID
	return a.PostID == b.PostID && a.Body == b.Body && sameParent && reflect.DeepEqual(a.Media, b.Media)
}

func (r *Repository) Update(commentID, body string, entities []models.MessageEntity) error {
	return r.db.Model(&models.Comment{}).Where("id = ? AND is_deleted = false", commentID).
		Updates(map[string]any{"body": body, "entities": models.MessageEntitySlice(entities), "updated_at": gorm.Expr("NOW()")}).Error
}

func (r *Repository) SoftDelete(commentID string) error {
	return r.db.Model(&models.Comment{}).
		Where("id = ? AND is_deleted = false", commentID).
		Update("is_deleted", true).Error
}

func (r *Repository) SetVote(commentID, userID string, value int) (*models.VoteResult, error) {
	var result models.VoteResult
	err := r.db.Transaction(func(tx *gorm.DB) error {
		lockKey := "comment-vote:" + commentID
		if err := tx.Exec("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", lockKey).Error; err != nil {
			return err
		}
		var comment models.Comment
		if err := tx.Select("id, post_id").Where("id = ? AND is_deleted = false AND is_hidden = false", commentID).
			Where("NOT EXISTS (SELECT 1 FROM user_blocks b WHERE b.blocker_id = ? AND b.blocked_id = comments.user_id)", userID).First(&comment).Error; err != nil {
			return err
		}
		if err := visiblePost(tx, comment.PostID, userID); err != nil {
			return err
		}
		var existing models.CommentVote
		err := tx.Where("comment_id = ? AND user_id = ?", commentID, userID).First(&existing).Error
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
				if err := tx.Create(&models.CommentVote{CommentID: commentID, UserID: userID, Type: newType}).Error; err != nil {
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
			FROM comment_votes WHERE comment_id = ?
		`, commentID).Scan(&result).Error; err != nil {
			return err
		}
		result.MyVote = newType
		result.Changed = changed
		if changed && newType == "upvote" {
			return notifications.RecordAppreciation(tx, comment.PostID, commentID, result.Upvotes)
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
