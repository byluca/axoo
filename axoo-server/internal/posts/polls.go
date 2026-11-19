package posts

import (
	"axoo-server/internal/apierror"
	"axoo-server/internal/models"
	"axoo-server/internal/textguard"
	"errors"
	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
	"reflect"
	"strings"
)

var ErrPollImmutable = errors.New("I post con sondaggio non possono essere modificati.")
var ErrPollAlreadyVoted = errors.New("Il voto al sondaggio è definitivo.")

func (s *Service) validatePoll(userID string, poll *models.PollInput) (models.PollOptions, error) {
	if len(poll.Options) < 2 || len(poll.Options) > 4 {
		return nil, errors.New("il sondaggio richiede da 2 a 4 opzioni")
	}
	options := make(models.PollOptions, 0, len(poll.Options))
	for _, option := range poll.Options {
		if strings.TrimSpace(option.Text) == "" {
			return nil, errors.New("le opzioni non possono essere vuote")
		}
		if err := textguard.ValidateBody("l'opzione", option.Text, 100, 0, option.Entities); err != nil {
			return nil, err
		}
		entities := option.Entities
		if entities == nil {
			entities = models.MessageEntitySlice{}
		}
		id := uuid.NewString()
		options = append(options, models.PollOptionDefinition{ID: id, Text: option.Text, Entities: entities})
	}
	return options, nil
}

func samePollPayload(a, b models.PollOptions) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		// IDs are server-owned; idempotency compares only client content.
		if a[i].Text != b[i].Text || len(a[i].Entities) != len(b[i].Entities) {
			return false
		}
		if !reflect.DeepEqual(a[i].Entities, b[i].Entities) {
			return false
		}
	}
	return true
}

func (s *Service) VotePoll(postID, userID, optionID string) (*models.PostPoll, error) {
	if _, err := uuid.Parse(postID); err != nil {
		return nil, apierror.Invalid(errors.New("post non valido"))
	}
	if _, err := uuid.Parse(optionID); err != nil {
		return nil, apierror.Invalid(errors.New("opzione non valida"))
	}
	return s.repo.SetPollVote(postID, userID, optionID)
}

func (h *Handler) votePoll(c *fiber.Ctx) error {
	var request struct {
		OptionID string `json:"option_id"`
	}
	if err := c.BodyParser(&request); err != nil {
		return c.Status(400).JSON(fiber.Map{"error": "dati non validi"})
	}
	poll, err := h.service.VotePoll(c.Params("id"), c.Locals("user_id").(string), request.OptionID)
	if errors.Is(err, ErrPollAlreadyVoted) {
		return c.Status(409).JSON(fiber.Map{"error": err.Error()})
	}
	if err != nil {
		return apierror.Respond(c, err)
	}
	return c.JSON(fiber.Map{"poll": poll})
}

// Load counts, the current selection and at most three avatars per option in batches.
func (r *Repository) LoadPolls(posts []models.Post, userID string) error {
	polls := make(map[string]*models.PostPoll)
	ids := make([]string, 0)
	for i := range posts {
		if len(posts[i].PollOptions) == 0 {
			continue
		}
		poll := &models.PostPoll{Options: make([]models.PostPollOption, len(posts[i].PollOptions))}
		for j, definition := range posts[i].PollOptions {
			poll.Options[j] = models.PostPollOption{PollOptionDefinition: definition, Voters: []models.PollVoter{}}
		}
		posts[i].Poll = poll
		polls[posts[i].ID] = poll
		ids = append(ids, posts[i].ID)
	}
	if len(ids) == 0 {
		return nil
	}
	var counts []struct {
		PostID, OptionID string
		Votes            int
	}
	if err := r.db.Model(&models.PollVote{}).Select("post_id, option_id, count(*) AS votes").Where("post_id IN ?", ids).Group("post_id, option_id").Scan(&counts).Error; err != nil {
		return err
	}
	for _, count := range counts {
		poll := polls[count.PostID]
		for i := range poll.Options {
			if poll.Options[i].ID == count.OptionID {
				poll.Options[i].Votes = count.Votes
				poll.TotalVotes += count.Votes
			}
		}
	}
	var mine []models.PollVote
	if err := r.db.Select("post_id, option_id").Where("post_id IN ? AND user_id = ?", ids, userID).Find(&mine).Error; err != nil {
		return err
	}
	for _, vote := range mine {
		polls[vote.PostID].MyOptionID = vote.OptionID
	}
	// Results and voter previews are revealed after this account has voted.
	votedIDs := make([]string, 0)
	for id, poll := range polls {
		if poll.MyOptionID != "" {
			votedIDs = append(votedIDs, id)
		} else {
			for i := range poll.Options {
				poll.Options[i].Votes = 0
			}
		}
	}
	if len(votedIDs) == 0 {
		return nil
	}
	var previews []struct {
		PostID, OptionID, UserID string
		AvatarConfig             models.AvatarConfig `gorm:"type:jsonb"`
	}
	err := r.db.Raw(`SELECT post_id, option_id, user_id, avatar_config FROM (
        SELECT v.post_id, v.option_id, v.user_id, u.avatar_config,
            row_number() OVER (PARTITION BY v.post_id, v.option_id ORDER BY v.created_at DESC, v.user_id) AS rank
        FROM poll_votes v JOIN users u ON u.id = v.user_id
        WHERE v.post_id IN ? AND NOT EXISTS (
            SELECT 1 FROM user_blocks b WHERE b.blocker_id = ? AND b.blocked_id = v.user_id
        )
    ) previews WHERE rank <= 3`, votedIDs, userID).Scan(&previews).Error
	if err != nil {
		return err
	}
	for _, voter := range previews {
		poll := polls[voter.PostID]
		for i := range poll.Options {
			if poll.Options[i].ID == voter.OptionID {
				poll.Options[i].Voters = append(poll.Options[i].Voters, models.PollVoter{UserID: voter.UserID, AvatarConfig: voter.AvatarConfig})
			}
		}
	}
	return nil
}

func (r *Repository) SetPollVote(postID, userID, optionID string) (*models.PostPoll, error) {
	var result *models.PostPoll
	err := r.db.Transaction(func(tx *gorm.DB) error {
		var post models.Post
		query := excludeHiddenPosts(excludeBlockedAuthors(tx.Model(&models.Post{}), userID, "posts.user_id"), userID)
		if err := query.Clauses(clause.Locking{Strength: "UPDATE"}).Select("id, poll_options").
			Where("posts.id = ? AND posts.is_deleted = false AND posts.is_hidden = false", postID).First(&post).Error; err != nil {
			return err
		}
		valid := false
		for _, option := range post.PollOptions {
			if option.ID == optionID {
				valid = true
			}
		}
		if !valid {
			return apierror.Invalid(errors.New("opzione non valida"))
		}
		var existing models.PollVote
		err := tx.Where("post_id = ? AND user_id = ?", postID, userID).First(&existing).Error
		switch {
		case err == nil:
			if existing.OptionID != optionID {
				return ErrPollAlreadyVoted
			}
		case errors.Is(err, gorm.ErrRecordNotFound):
			if err := tx.Create(&models.PollVote{PostID: postID, UserID: userID, OptionID: optionID}).Error; err != nil {
				return err
			}
		default:
			return err
		}
		posts := []models.Post{post}
		if err := NewRepository(tx).LoadPolls(posts, userID); err != nil {
			return err
		}
		result = posts[0].Poll
		return nil
	})
	return result, err
}
