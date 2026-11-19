package comments

import (
	"errors"
	"testing"

	"axoo-server/internal/models"
)

type createRepository struct {
	CommentRepository
	created bool
	err     error
}

func (r createRepository) Create(comment *models.Comment) (bool, error) {
	comment.ID = "saved-comment"
	return r.created, r.err
}

type recordingNotifier struct{ comments []models.Comment }

func (n *recordingNotifier) CommentCreated(comment models.Comment) {
	n.comments = append(n.comments, comment)
}

func TestCreateCommentNotifiesOnlyAfterSuccessfulNewInsert(t *testing.T) {
	for _, tc := range []struct {
		name    string
		created bool
		err     error
		want    int
	}{
		{name: "new comment", created: true, want: 1},
		{name: "idempotent retry"},
		{name: "failed insert", err: errors.New("database unavailable")},
		{name: "failed commit", created: true, err: errors.New("commit failed")},
	} {
		t.Run(tc.name, func(t *testing.T) {
			notifier := &recordingNotifier{}
			service := NewService(createRepository{created: tc.created, err: tc.err}, notifier)
			comment, created, err := service.CreateComment("author", "post", "", "hello", nil, "", nil)
			if !errors.Is(err, tc.err) {
				t.Fatalf("error = %v, want %v", err, tc.err)
			}
			if len(notifier.comments) != tc.want {
				t.Fatalf("notifications = %d, want %d", len(notifier.comments), tc.want)
			}
			if tc.want == 1 && (notifier.comments[0].ID != comment.ID || !created) {
				t.Fatal("notification does not refer to the committed comment")
			}
		})
	}
}

func TestCreateCommentValidationDoesNotNotify(t *testing.T) {
	notifier := &recordingNotifier{}
	// A nil repository would panic if validation reached the database.
	service := NewService(nil, notifier)
	_, _, err := service.CreateComment("author", "post", "invalid-client-id", "hello", nil, "", nil)
	if err == nil || len(notifier.comments) != 0 {
		t.Fatal("invalid request reached the notifier")
	}
}

func TestMediaOnlyCommentAndIdempotency(t *testing.T) {
	for _, kind := range []string{"gif", "sticker"} {
		t.Run(kind, func(t *testing.T) {
			media := &models.ImageMeta{URL: "https://media.giphy.com/media/abc/giphy.gif", ThumbnailURL: "https://media.giphy.com/media/abc/200.gif", Type: kind, Provider: "giphy", ProviderID: "abc", Width: 200, Height: 100}
			notifier := &recordingNotifier{}
			service := NewService(createRepository{created: true}, notifier)
			comment, created, err := service.CreateComment("author", "post", "", "", nil, "", media)
			if err != nil || !created || comment.Media == nil || comment.Media.Type != kind || len(notifier.comments) != 1 {
				t.Fatalf("media comment: %+v, %v", comment, err)
			}
			retry := *comment
			retry.Media = &models.ImageMeta{}
			*retry.Media = *media
			if !sameCommentRequest(*comment, retry) {
				t.Fatal("same attachment must deduplicate")
			}
			retry.Media.URL = "https://media.giphy.com/media/other/giphy.gif"
			if sameCommentRequest(*comment, retry) {
				t.Fatal("different attachment must conflict")
			}
			retry.Media = nil
			if sameCommentRequest(*comment, retry) {
				t.Fatal("missing attachment must conflict")
			}
			if commentTombstone(*comment).Media != nil {
				t.Fatal("deleted media must be removed")
			}
		})
	}
}

func TestInvalidCommentMediaNeverReachesRepository(t *testing.T) {
	valid := models.ImageMeta{URL: "https://media.giphy.com/media/abc/giphy.gif", Type: "sticker", Provider: "giphy", ProviderID: "abc", Width: 200, Height: 100}
	for _, change := range []func(*models.ImageMeta){
		func(m *models.ImageMeta) { m.URL = "https://giphy.com.example.org/file.gif" },
		func(m *models.ImageMeta) { m.URL = "http://media.giphy.com/file.gif" },
		func(m *models.ImageMeta) { m.URL = "" },
		func(m *models.ImageMeta) { m.ThumbnailURL = "https://example.org/file.gif" },
		func(m *models.ImageMeta) { m.Type = "photo" },
		func(m *models.ImageMeta) { m.Provider = "axoo" },
		func(m *models.ImageMeta) { m.Width = 0 },
	} {
		media := valid
		change(&media)
		_, _, err := NewService(nil).CreateComment("author", "post", "", "", nil, "", &media)
		if err == nil {
			t.Fatalf("accepted invalid media: %+v", media)
		}
	}
	if err := validateCommentBody("", []models.MessageEntity{{Type: "custom_emoji", Offset: 0, Length: 1}}, &valid); err == nil {
		t.Fatal("media cannot bypass entity validation")
	}
	if err := validateCommentBody("", nil, nil); err == nil {
		t.Fatal("empty comment accepted")
	}
}

type editMediaRepository struct {
	CommentRepository
	comment models.Comment
	updated bool
}

func (r *editMediaRepository) FindByID(string) (*models.Comment, error) { return &r.comment, nil }
func (r *editMediaRepository) Update(_ string, body string, _ []models.MessageEntity) error {
	r.updated = true
	r.comment.Body = body
	return nil
}
func TestTextEditKeepsCommentAttachment(t *testing.T) {
	repo := &editMediaRepository{comment: models.Comment{UserID: "owner", Body: "caption", Media: &models.ImageMeta{Type: "gif"}}}
	if err := NewService(repo).UpdateComment("id", "owner", "", nil); err != nil {
		t.Fatal(err)
	}
	if !repo.updated || repo.comment.Media == nil {
		t.Fatal("media lost during text edit")
	}
}

func TestSentStickerCannotBeEdited(t *testing.T) {
	for _, body := range []string{"", "aggiungo testo", "❤️"} {
		repo := &editMediaRepository{comment: models.Comment{UserID: "owner", Media: &models.ImageMeta{Type: "sticker"}}}
		if err := NewService(repo).UpdateComment("id", "owner", body, nil); err == nil || repo.updated {
			t.Fatalf("sticker edited with body %q: %v", body, err)
		}
	}
}
