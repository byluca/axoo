package posts

import (
	"errors"
	"fmt"
	"net/url"
	"strconv"
	"strings"
	"time"

	"axoo-server/internal/apierror"
	remotemedia "axoo-server/internal/media"
	"axoo-server/internal/models"
	"axoo-server/internal/textguard"

	"github.com/google/uuid"
)

// UserDirectory exposes only the author data needed by the posts domain.
type UserDirectory interface {
	FindNicknames(userIDs []string) (map[string]string, error)
	FindUniversitiesForUsers(userIDs []string) (map[string]models.University, error)
}

// PaginatedPosts è la struct di risposta per la lista post paginata.
type PaginatedPosts struct {
	Posts      []models.Post `json:"posts"`
	Limit      int           `json:"limit"`
	HasMore    bool          `json:"has_more"`
	NextCursor string        `json:"next_cursor"`
}

type Service struct {
	repo          PostRepository
	avatarLoader  AvatarConfigLoader
	userDirectory UserDirectory
}

// costruttore in Golang
func NewService(
	repo PostRepository,
	avatarLoader AvatarConfigLoader,
	users UserDirectory,
) *Service {
	service := &Service{
		repo:          repo,
		avatarLoader:  avatarLoader,
		userDirectory: users,
	}
	return service
}

func (s *Service) ListPosts(userID, order, cursor string, limit, offset int) (*PaginatedPosts, error) {
	return s.listPosts(userID, order, cursor, limit, offset, false, "")
}

func (s *Service) ListMyPosts(userID, cursor string, limit int) (*PaginatedPosts, error) {
	return s.listPosts(userID, "new", cursor, limit, 0, true, "")
}

func (s *Service) ListUserPosts(viewerID, authorID, cursor string, limit int) (*PaginatedPosts, error) {
	if _, err := uuid.Parse(authorID); err != nil {
		return nil, apierror.Invalid(errors.New("profilo non valido"))
	}
	return s.listPosts(viewerID, "new", cursor, limit, 0, false, authorID)
}

func (s *Service) listPosts(userID, order, cursor string, limit, offset int, mine bool, authorID string) (*PaginatedPosts, error) {
	if limit <= 0 || limit > 50 {
		limit = 20
	}
	if order == "" {
		order = "hot"
	}

	var cityID string
	if !mine {
		var err error
		cityID, err = s.repo.CityIDForUser(userID)
		if err != nil {
			return nil, err
		}
	}
	posts, err := s.repo.FindAll(cityID, userID, ListFilter{
		AuthorID: authorID,
		Mine:     mine,
		Order:    order,
		Cursor:   cursor,
		Limit:    limit,
		Offset:   offset,
	})
	if err != nil {
		return nil, err
	}

	if err := s.attachAuthorMetadata(posts); err != nil {
		return nil, err
	}

	var nextCursor string
	if len(posts) == limit {
		last := posts[len(posts)-1]
		if order == "new" {
			nextCursor = last.CreatedAt.UTC().Format(time.RFC3339Nano) + "|" + last.ID
		} else {
			// Per "hot", il cursore è semplicemente il prossimo offset intero
			currentOffset := offset
			if cursor != "" {
				if parsed, err := strconv.Atoi(cursor); err == nil {
					currentOffset = parsed
				}
			}
			nextCursor = strconv.Itoa(currentOffset + limit)
		}
	}

	return &PaginatedPosts{
		Posts:      posts,
		Limit:      limit,
		HasMore:    len(posts) == limit,
		NextCursor: nextCursor,
	}, nil
}

func (s *Service) GetPost(id, userID string) (*models.Post, error) {
	post, err := s.repo.FindByID(id, userID)
	if err != nil {
		return nil, err
	}

	posts := []models.Post{*post}
	if err := s.attachAuthorMetadata(posts); err != nil {
		return nil, err
	}
	*post = posts[0]

	return post, nil
}

func (s *Service) NewFeedUnreadCount(userID string) (int, error) {
	cityID, err := s.repo.CityIDForUser(userID)
	if err != nil {
		return 0, err
	}
	return s.repo.NewFeedUnreadCount(cityID, userID)
}

func (s *Service) MarkNewFeedSeen(userID string, seenThrough time.Time) error {
	if seenThrough.IsZero() || seenThrough.After(time.Now().UTC().Add(time.Minute)) {
		return apierror.Invalid(errors.New("data di lettura non valida"))
	}
	return s.repo.MarkNewFeedSeen(userID, seenThrough)
}

var validFlairs = map[string]bool{
	"Esami": true, "Meme": true, "Spotted": true,
	"Confessioni": true,
	"Party":       true,
	"Dell'Andro":  true, "Fraccacreta": true,
}

const (
	postMaxRunes = 500
)

func (s *Service) CreatePost(userID, clientID, body, flair string, media *models.ImageMeta, entities []models.MessageEntity, poll ...*models.PollInput) (*models.Post, bool, error) {
	var options models.PollOptions
	if len(poll) > 0 && poll[0] != nil {
		var err error
		options, err = s.validatePoll(userID, poll[0])
		if err != nil {
			return nil, false, err
		}
	}
	if err := validatePostContent(body, entities); err != nil {
		return nil, false, err
	}
	if flair != "" && !validFlairs[flair] {
		return nil, false, errors.New("flair non valido")
	}
	normalizedMedia, err := normalizeAndValidateMedia(media)
	if err != nil {
		return nil, false, err
	}
	media = normalizedMedia
	var normalizedClientID *string
	if clientID != "" {
		parsed, err := uuid.Parse(clientID)
		if err != nil {
			return nil, false, errors.New("client_id non valido")
		}
		value := parsed.String()
		normalizedClientID = &value
	}
	cityID, err := s.repo.CityIDForUser(userID)
	if err != nil {
		return nil, false, errors.New("campus non trovato")
	}
	post := &models.Post{
		PollOptions: options,
		UserID:      userID,
		ClientID:    normalizedClientID,
		CityID:      cityID,
		Body:        body,
		Flair:       flair,
		Media:       media,
		Entities:    models.MessageEntitySlice(entities),
	}
	created, err := s.repo.Create(post)
	if err != nil {
		return nil, false, err
	}
	posts := []models.Post{*post}
	if len(post.PollOptions) > 0 {
		if err := s.repo.LoadPolls(posts, userID); err != nil {
			return nil, false, err
		}
	}
	if err := s.attachAuthorMetadata(posts); err != nil {
		return nil, false, err
	}
	*post = posts[0]
	return post, created, nil
}

func (s *Service) UpdatePost(postID, userID, body, flair string, media *models.ImageMeta, entities []models.MessageEntity) error {
	ownerID, err := s.repo.FindOwner(postID)
	if err != nil {
		return err
	}
	if userID != ownerID {
		return apierror.ErrForbidden
	}
	if err := validatePostContent(body, entities); err != nil {
		return apierror.Invalid(err)
	}
	if flair != "" && !validFlairs[flair] {
		return apierror.Invalid(errors.New("flair non valido"))
	}
	normalizedMedia, err := normalizeAndValidateMedia(media)
	if err != nil {
		return apierror.Invalid(err)
	}
	return s.repo.Update(postID, body, flair, normalizedMedia, entities)
}

func validatePostContent(body string, entities []models.MessageEntity) error {
	if strings.TrimSpace(body) == "" {
		return errors.New("il testo del post è obbligatorio")
	}
	return textguard.ValidateBody("il post", body, postMaxRunes, 0, entities)
}

func normalizeAndValidateMedia(media *models.ImageMeta) (*models.ImageMeta, error) {
	if media == nil {
		return nil, nil
	}
	normalized := *media
	if normalized.Provider == "klipy" || normalized.Provider == "aup" || normalized.Provider == "axoo" {
		if err := remotemedia.ValidateRemoteAttachment(&normalized); err != nil {
			return nil, err
		}
	}
	if strings.Contains(normalized.URL, "/storage/v1/object/public/post-images/") && normalized.Type != "photo" {
		return nil, errors.New("tipo media non valido")
	}
	if !isValidHTTPSURL(normalized.URL) || len(normalized.URL) > maxMediaURLLength {
		return nil, errors.New("URL media non valido")
	}
	if normalized.ThumbnailURL != "" &&
		(!isValidHTTPSURL(normalized.ThumbnailURL) || len(normalized.ThumbnailURL) > maxMediaURLLength) {
		normalized.ThumbnailURL = ""
	}
	if normalized.PreviewFallbackURL != "" &&
		(!isValidHTTPSURL(normalized.PreviewFallbackURL) || len(normalized.PreviewFallbackURL) > maxMediaURLLength) {
		normalized.PreviewFallbackURL = ""
	}
	if normalized.Width < 0 || normalized.Height < 0 {
		return nil, errors.New("dimensioni media non valide")
	}
	switch normalized.Type {
	case "gif", "sticker", "meme":
	case "photo":
		if normalized.Width < 1 || normalized.Height < 1 || normalized.Width > 2048 || normalized.Height > 2048 {
			return nil, errors.New("dimensioni foto non valide")
		}
		normalized.MIMEType = "image/jpeg"
		normalized.ThumbnailURL = ""
		normalized.PreviewFallbackURL = ""
	default:
		return nil, errors.New("tipo media non valido")
	}
	return &normalized, nil
}

func isValidHTTPSURL(raw string) bool {
	parsed, err := url.ParseRequestURI(raw)
	return err == nil && parsed.Scheme == "https" && parsed.Host != ""
}

const maxMediaURLLength = 8 * 1024

func (s *Service) SoftDeletePost(postID, userID string) error {
	ownerID, err := s.repo.FindOwner(postID)
	if err != nil {
		return errors.New("post non trovato")
	}

	if userID != ownerID {
		return errors.New("non autorizzato")
	}

	return s.repo.SoftDelete(postID)
}

func (s *Service) VotePost(postID, userID string, value int) (*models.VoteResult, error) {
	if value < -1 || value > 1 {
		return nil, errors.New("value deve essere -1, 0 o 1")
	}
	result, err := s.repo.SetVote(postID, userID, value)
	if err != nil {
		return nil, err
	}

	if result.Changed && result.MyVote == "upvote" && isMilestone(result.Upvotes) {
	}

	return result, nil
}

var upvoteMilestones = []int{10, 25, 50, 100, 250, 500, 1000, 2500, 5000}

func isMilestone(n int) bool {
	for _, m := range upvoteMilestones {
		if n == m {
			return true
		}
	}
	return false
}

// attachAuthorMetadata guarantees that every post response contains its
// server-owned pseudonym. Avatar configuration remains optional.
func (s *Service) attachAuthorMetadata(posts []models.Post) error {
	if len(posts) == 0 {
		return nil
	}
	seen := make(map[string]bool)
	userIDs := make([]string, 0, len(posts))
	for _, p := range posts {
		if !seen[p.UserID] {
			seen[p.UserID] = true
			userIDs = append(userIDs, p.UserID)
		}
	}

	nicknameMap, err := s.userDirectory.FindNicknames(userIDs)
	if err != nil {
		return fmt.Errorf("load post nicknames: %w", err)
	}
	for i := range posts {
		nickname := strings.TrimSpace(nicknameMap[posts[i].UserID])
		if nickname == "" {
			return fmt.Errorf("missing nickname for post author %s", posts[i].UserID)
		}
		posts[i].Nickname = nickname
	}

	universityMap, err := s.userDirectory.FindUniversitiesForUsers(userIDs)
	if err != nil {
		return fmt.Errorf("load post university names: %w", err)
	}
	for i := range posts {
		university, ok := universityMap[posts[i].UserID]
		if !ok || strings.TrimSpace(university.Name) == "" {
			return fmt.Errorf("missing university name for post %s", posts[i].ID)
		}
		posts[i].UniversityID = university.ID
		posts[i].UniversityName = university.Name
	}

	if s.avatarLoader != nil {
		configMap, loadErr := s.avatarLoader.LoadAvatarConfigs(userIDs)
		if loadErr == nil {
			for i := range posts {
				posts[i].AvatarConfig = configMap[posts[i].UserID]
			}
		}
	}
	return nil
}
