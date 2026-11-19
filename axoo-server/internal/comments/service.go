package comments

//IL SERVICE è lo Chef (Business Logic Layer).
// È il cervello del pacchetto. Prende i dati crudi dal Cameriere (HANDLER), applica le regole aziendali
// (es: controlla la lunghezza dei commenti, verifica i permessi, invia notifiche),
// e solo alla fine ordina al Magazziniere di mettere la roba in frigo.

import (
	"errors"
	"strings"

	"axoo-server/internal/apierror"
	"axoo-server/internal/media"
	"axoo-server/internal/models"
	"axoo-server/internal/textguard"

	"github.com/google/uuid"
)

type Service struct {
	repo     CommentRepository
	notifier CommentNotifier
}

type CommentNotifier interface {
	CommentCreated(models.Comment)
}

// funzione x fare il costruttore della struct Service (visto che su golang non c'e' new() come in java)
func NewService(repo CommentRepository, notifiers ...CommentNotifier) *Service {
	service := &Service{repo: repo}
	if len(notifiers) > 0 {
		service.notifier = notifiers[0]
	}
	return service
}

func (s *Service) ListComments(postID, userID string) ([]models.Comment, error) {
	return s.repo.FindByPostID(postID, userID)
}

const (
	commentMaxRunes     = 500 // Sincronizzato con il frontend (CommentComposerInputBar.kt)
	commentMaxEmojiTags = 0   // Il frontend non blocca le emoji, nessun limite qui
)

func (s *Service) CreateComment(userID, postID, clientID, body string, entities []models.MessageEntity, replyToCommentID string, media *models.ImageMeta) (*models.Comment, bool, error) {
	if err := validateCommentMedia(media); err != nil {
		return nil, false, apierror.Invalid(err)
	}
	if err := validateCommentBody(body, entities, media); err != nil {
		return nil, false, apierror.Invalid(err)
	}
	var parentCommentID *string
	if replyToCommentID != "" {
		parsed, parseErr := uuid.Parse(replyToCommentID)
		if parseErr != nil {
			return nil, false, apierror.Invalid(errors.New("commento a cui rispondere non valido"))
		}
		replyToCommentID = parsed.String()
		parentCommentID = &replyToCommentID
	}
	var normalizedClientID *string
	if clientID != "" {
		parsed, err := uuid.Parse(clientID)
		if err != nil {
			return nil, false, apierror.Invalid(errors.New("client_id non valido"))
		}
		value := parsed.String()
		normalizedClientID = &value
	}
	comment := &models.Comment{PostID: postID, UserID: userID, ClientID: normalizedClientID, Body: body, Entities: models.MessageEntitySlice(entities), ParentCommentID: parentCommentID, Media: media}
	created, err := s.repo.Create(comment)
	if err != nil {
		return nil, false, err
	}
	if created && s.notifier != nil {
		s.notifier.CommentCreated(*comment)
	}
	return comment, created, nil
}

func (s *Service) UpdateComment(commentID, userID, body string, entities []models.MessageEntity) error {
	comment, err := s.repo.FindByID(commentID)
	if err != nil {
		return err
	}
	if userID != comment.UserID {
		return apierror.ErrForbidden
	}
	if comment.Media != nil && comment.Media.Type == "sticker" {
		return apierror.Invalid(errors.New("gli sticker inviati non possono essere modificati"))
	}
	if err := validateCommentBody(body, entities, comment.Media); err != nil {
		return apierror.Invalid(err)
	}
	if err := s.repo.Update(commentID, body, entities); err != nil {
		return err
	}
	return nil
}

func (s *Service) DeleteComment(commentID, userID string) error {
	ownerID, err := s.repo.FindOwner(commentID)
	if err != nil {
		return errors.New("commento non trovato")
	}
	if userID != ownerID {
		return errors.New("non autorizzato")
	}
	return s.repo.SoftDelete(commentID)
}

func (s *Service) VoteComment(commentID, userID string, value int) (*models.VoteResult, error) {
	if value < -1 || value > 1 {
		return nil, errors.New("value deve essere -1, 0 o 1")
	}
	result, err := s.repo.SetVote(commentID, userID, value)
	if err != nil {
		return nil, err
	}
	return result, nil
}

// An attachment is optional; a media-only comment still validates entity ranges.
func validateCommentBody(body string, entities []models.MessageEntity, media *models.ImageMeta) error {
	if body == "" && media != nil && len(entities) == 0 {
		return nil
	}
	if strings.TrimSpace(body) == "" {
		return errors.New("il commento non può essere vuoto")
	}
	return textguard.ValidateBody("il commento", body, commentMaxRunes, commentMaxEmojiTags, entities)
}
func validateCommentMedia(attachment *models.ImageMeta) error {
	if attachment == nil {
		return nil
	}
	if attachment.Type != "gif" && attachment.Type != "sticker" {
		return errors.New("questo allegato è disponibile nei post")
	}
	return media.ValidateRemoteAttachment(attachment)
}
