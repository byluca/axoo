package push

import (
	"context"
	"errors"
	"io"
	"net/http"
	"strings"
	"testing"
	"time"

	"axoo-server/internal/models"

	firebase "firebase.google.com/go/v4"
	"firebase.google.com/go/v4/messaging"
	"google.golang.org/api/option"
)

type notificationStore struct {
	installations []Installation
	lookups       int
	err           error
	removed       []Installation
}

func (r *notificationStore) ForComment(ctx context.Context, _ string) ([]Installation, error) {
	r.lookups++
	return r.installations, r.err
}

func (r *notificationStore) Remove(_ context.Context, userID, id, registration string) error {
	r.removed = append(r.removed, Installation{UserID: userID, InstallationID: id, Registration: registration})
	return nil
}

type recordingSender struct {
	messages []*messaging.Message
	err      error
}

func (s *recordingSender) Send(ctx context.Context, message *messaging.Message) (string, error) {
	deadline, ok := ctx.Deadline()
	if !ok || time.Until(deadline) > 3*time.Second {
		return "", errors.New("missing delivery deadline")
	}
	s.messages = append(s.messages, message)
	return "message-id", s.err
}

func TestCommentPushPayloadAndMultipleInstallations(t *testing.T) {
	parentID := "parent"
	for _, tc := range []struct {
		name, kind string
		parent     *string
	}{
		{name: "comment", kind: "post_comment"},
		{name: "reply", kind: "comment_reply", parent: &parentID},
	} {
		t.Run(tc.name, func(t *testing.T) {
			repo := &notificationStore{installations: []Installation{
				{UserID: "recipient", Registration: "android"},
				{UserID: "recipient", Registration: "ios"},
			}}
			sender := &recordingSender{}
			NewService(repo, sender).CommentCreated(models.Comment{ID: "comment", PostID: "post", Body: "private text", Nickname: "Giulia", ParentCommentID: tc.parent})
			if len(sender.messages) != 2 {
				t.Fatalf("messages = %d, want 2", len(sender.messages))
			}
			for i, message := range sender.messages {
				if message.Token != repo.installations[i].Registration || message.Data["type"] != tc.kind || message.Data["post_id"] != "post" || message.Data["comment_id"] != "comment" || message.Data["recipient_id"] != "recipient" {
					t.Fatalf("incorrect target: %+v", message)
				}
				if message.Notification.Title != "Axoo" || strings.Contains(message.Notification.Body, "private text") || len(message.Data) != 5 {
					t.Fatal("push must contain the nickname and navigation identifiers")
				}
				suffix := " ha commentato il tuo post"
				if tc.parent != nil {
					suffix = " ha risposto al tuo commento"
				}
				if message.Notification.Body != "Giulia"+suffix || message.Data["message"] != message.Notification.Body {
					t.Fatal("missing author nickname")
				}
				if message.Android.Notification.ChannelID != "interactions" || message.APNS.Payload.Aps.Sound != "default" {
					t.Fatal("missing native notification configuration")
				}
			}
		})
	}
}

func TestCommentPushFailuresAndDisabledSending(t *testing.T) {
	t.Run("disabled", func(t *testing.T) {
		repo := &notificationStore{}
		NewService(repo, nil).CommentCreated(models.Comment{ID: "comment"})
		if repo.lookups != 0 {
			t.Fatal("disabled push queried the database")
		}
	})
	t.Run("database failure", func(t *testing.T) {
		repo := &notificationStore{err: errors.New("unavailable")}
		sender := &recordingSender{}
		NewService(repo, sender).CommentCreated(models.Comment{ID: "comment"})
		if len(sender.messages) != 0 {
			t.Fatal("sent despite failed recipient lookup")
		}
	})
	t.Run("send failure does not stop other installations", func(t *testing.T) {
		repo := &notificationStore{installations: []Installation{{Registration: "first"}, {Registration: "second"}}}
		sender := &recordingSender{err: errors.New("unavailable")}
		NewService(repo, sender).CommentCreated(models.Comment{ID: "comment"})
		if len(sender.messages) != 2 || len(repo.removed) != 0 {
			t.Fatal("transient failure interrupted delivery or removed a registration")
		}
	})
}

type roundTripFunc func(*http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }

func TestFCMSenderUsesExistingJSONCredentials(t *testing.T) {
	t.Setenv("GOOGLE_CLOUD_PROJECT", "")
	t.Setenv("GCLOUD_PROJECT", "")
	// Synthetic credentials: initialization does not fetch an OAuth token or send a push.
	credentials := `{"type":"service_account","project_id":"test-project","client_email":"test@test-project.iam.gserviceaccount.com","private_key":"test-key","token_uri":"https://oauth2.googleapis.com/token"}`
	sender, err := NewFCMSender(context.Background(), "", credentials)
	if err != nil || sender == nil {
		t.Fatalf("JSON credentials initialization failed: %v", err)
	}
	if _, err := NewFCMSender(context.Background(), "explicit-project", "invalid-json"); err == nil {
		t.Fatal("invalid JSON credentials were accepted")
	}
	missingProject := strings.Replace(credentials, `"project_id":"test-project",`, "", 1)
	if _, err := NewFCMSender(context.Background(), "", missingProject); err == nil {
		t.Fatal("missing project was accepted")
	}
	if _, err := NewFCMSender(context.Background(), "explicit-project", missingProject); err != nil {
		t.Fatalf("explicit project override failed: %v", err)
	}
}

func TestFCMRemovesOnlyUnregisteredInstallations(t *testing.T) {
	for _, tc := range []struct {
		code    string
		status  int
		removed int
	}{
		{code: "UNREGISTERED", status: 404, removed: 1},
		{code: "INVALID_ARGUMENT", status: 400},
		{code: "SENDER_ID_MISMATCH", status: 403},
	} {
		t.Run(tc.code, func(t *testing.T) {
			client := &http.Client{Transport: roundTripFunc(func(request *http.Request) (*http.Response, error) {
				return &http.Response{
					StatusCode: tc.status, Header: http.Header{"Content-Type": {"application/json"}},
					Body: io.NopCloser(strings.NewReader(`{"error":{"status":"NOT_FOUND","message":"FCM failure","details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"` + tc.code + `"}]}}`)),
				}, nil
			})}
			app, err := firebase.NewApp(context.Background(), &firebase.Config{ProjectID: "test-project"}, option.WithHTTPClient(client))
			if err != nil {
				t.Fatal(err)
			}
			sender, err := app.Messaging(context.Background())
			if err != nil {
				t.Fatal(err)
			}
			repo := &notificationStore{installations: []Installation{{UserID: "recipient", InstallationID: installationID, Registration: "old-registration"}}}
			NewService(repo, sender).CommentCreated(models.Comment{ID: "comment", PostID: "post"})
			if len(repo.removed) != tc.removed {
				t.Fatalf("removed = %d, want %d", len(repo.removed), tc.removed)
			}
			if tc.removed == 1 && repo.removed[0] != repo.installations[0] {
				t.Fatal("cleanup did not preserve the original registration/account snapshot")
			}
		})
	}
}
