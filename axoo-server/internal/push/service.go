package push

import (
	"context"
	"log"
	"time"

	"axoo-server/internal/models"
	"axoo-server/internal/notifications"

	firebase "firebase.google.com/go/v4"
	"firebase.google.com/go/v4/errorutils"
	"firebase.google.com/go/v4/messaging"
	"google.golang.org/api/option"
)

type Sender interface {
	Send(context.Context, *messaging.Message) (string, error)
}

func NewFCMSender(ctx context.Context, projectID, credentialsJSON string) (Sender, error) {
	var opts []option.ClientOption
	if credentialsJSON != "" {
		opts = append(opts, option.WithCredentialsJSON([]byte(credentialsJSON)))
	}
	app, err := firebase.NewApp(ctx, &firebase.Config{ProjectID: projectID}, opts...)
	if err != nil {
		return nil, err
	}
	return app.Messaging(ctx)
}

type commentInstallations interface {
	ForComment(context.Context, string) ([]Installation, error)
	Remove(context.Context, string, string, string) error
}

type Service struct {
	repo   commentInstallations
	sender Sender
}

func NewService(repo commentInstallations, sender Sender) *Service {
	return &Service{repo: repo, sender: sender}
}

// CommentCreated runs after the comment commits. Delivery is best effort, with no persistent retries.
func (s *Service) CommentCreated(comment models.Comment) {
	if s.sender == nil {
		log.Printf("push comment_id=%s: delivery disabled", comment.ID)
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	installations, err := s.repo.ForComment(ctx, comment.ID)
	if err != nil {
		log.Printf("push comment_id=%s: installation lookup failed", comment.ID)
		return
	}
	log.Printf("push comment_id=%s: installations=%d", comment.ID, len(installations))
	kind := "post_comment"
	if comment.ParentCommentID != nil {
		kind = "comment_reply"
	}
	body := notifications.Message(kind, comment.Nickname)
	for _, installation := range installations {
		if ctx.Err() != nil {
			log.Printf("push comment_id=%s: delivery timeout", comment.ID)
			return
		}
		_, err := s.sender.Send(ctx, &messaging.Message{
			Token:        installation.Registration,
			Notification: &messaging.Notification{Title: "Axoo", Body: body},
			Data: map[string]string{
				"type": kind, "post_id": comment.PostID, "comment_id": comment.ID, "recipient_id": installation.UserID, "message": body,
			},
			Android: &messaging.AndroidConfig{
				Priority:     "high",
				Notification: &messaging.AndroidNotification{ChannelID: "interactions", Sound: "default"},
			},
			APNS: &messaging.APNSConfig{
				Headers: map[string]string{"apns-push-type": "alert", "apns-priority": "10"},
				Payload: &messaging.APNSPayload{Aps: &messaging.Aps{Sound: "default"}},
			},
		})
		if messaging.IsUnregistered(err) {
			log.Printf("push comment_id=%s: registration expired", comment.ID)
			if err := s.repo.Remove(ctx, installation.UserID, installation.InstallationID, installation.Registration); err != nil {
				log.Printf("push comment_id=%s: expired registration cleanup failed", comment.ID)
			}
		} else if err != nil {
			status := 0
			if response := errorutils.HTTPResponse(err); response != nil {
				status = response.StatusCode
			}
			log.Printf("push comment_id=%s: FCM send failed status=%d timeout=%t", comment.ID, status, ctx.Err() != nil)
		} else {
			log.Printf("push comment_id=%s: FCM accepted", comment.ID)
		}
	}
}
