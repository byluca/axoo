package events

import (
	"axoo-server/internal/apierror"
	"axoo-server/internal/models"
	"encoding/base64"
	"encoding/json"
	"errors"
	"strings"
	"time"
	_ "time/tzdata"
	"unicode/utf8"

	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
)

var (
	ErrForbidden = errors.New("solo gli admin possono gestire gli eventi")
	ErrInvalid   = errors.New("dati evento non validi")
	ErrNotFound  = errors.New("evento non disponibile")
)

type Event struct {
	OrganizerID           *string             `json:"organizer_id,omitempty" gorm:"type:uuid"`
	OrganizerName         string              `json:"organizer_name" gorm:"->"`
	OrganizerAvatarConfig models.AvatarConfig `json:"organizer_avatar_config,omitempty" gorm:"->;type:jsonb"`
	EndsAt                *time.Time          `json:"ends_at,omitempty"`
	Address               string              `json:"address"`
	EntryInfo             string              `json:"entry_info"`
	CanManage             bool                `json:"can_manage" gorm:"-"`
	Poster                *models.ImageMeta   `json:"poster,omitempty" gorm:"type:jsonb"`
	ID                    string              `json:"id" gorm:"primaryKey;type:uuid"`
	CityID                string              `json:"-"`
	Title                 string              `json:"title"`
	Location              string              `json:"location"`
	Description           string              `json:"description"`
	StartsAt              time.Time           `json:"starts_at"`
	CreatedAt             time.Time           `json:"-"`
}

func (Event) TableName() string { return "campus_events" }

type Input struct {
	EndsAt      *time.Time        `json:"ends_at,omitempty"`
	Address     string            `json:"address"`
	EntryInfo   string            `json:"entry_info"`
	Poster      *models.ImageMeta `json:"poster,omitempty"`
	Title       string            `json:"title"`
	Location    string            `json:"location"`
	Description string            `json:"description"`
	StartsAt    time.Time         `json:"starts_at"`
}

type Audience struct {
	Nickname     string
	AvatarConfig models.AvatarConfig
	CityID       string
	IsAdmin      bool
}
type Cursor struct {
	StartsAt time.Time
	ID       string
}
type Page struct {
	Events     []Event `json:"events"`
	NextCursor string  `json:"next_cursor,omitempty"`
	CanManage  bool    `json:"can_manage"`
}

type repository interface {
	Audience(string) (Audience, error)
	List(string, time.Time, *Cursor, int) ([]Event, error)
	Create(string, *Event) error
	Delete(string, string) error
	Get(string, string) (*Event, error)
	Update(string, string, string, Input) error
}

type Service struct {
	repo repository
	now  func() time.Time
}

func NewService(repo repository) *Service { return &Service{repo: repo, now: time.Now} }

func (s *Service) List(userID, rawCursor string) (*Page, error) {
	audience, err := s.repo.Audience(userID)
	if err != nil {
		return nil, err
	}
	var cursor *Cursor
	if rawCursor != "" {
		data, err := base64.RawURLEncoding.DecodeString(rawCursor)
		cursor = &Cursor{}
		if err != nil || json.Unmarshal(data, cursor) != nil || cursor.StartsAt.IsZero() {
			return nil, ErrInvalid
		}
		if _, err := uuid.Parse(cursor.ID); err != nil {
			return nil, ErrInvalid
		}
	}
	rome, _ := time.LoadLocation("Europe/Rome")
	now := s.now().In(rome)
	today := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, rome)
	items, err := s.repo.List(audience.CityID, today, cursor, 21)
	if err != nil {
		return nil, err
	}
	for i := range items {
		items[i].CanManage = audience.IsAdmin
	}
	page := &Page{Events: items, CanManage: audience.IsAdmin}
	if len(items) > 20 {
		page.Events = items[:20]
		last := page.Events[19]
		data, _ := json.Marshal(Cursor{StartsAt: last.StartsAt, ID: last.ID})
		page.NextCursor = base64.RawURLEncoding.EncodeToString(data)
	}
	if page.Events == nil {
		page.Events = []Event{}
	}
	return page, nil
}

func (s *Service) Create(userID string, input Input) (*Event, error) {
	audience, err := s.repo.Audience(userID)
	if err != nil {
		return nil, err
	}
	if !audience.IsAdmin {
		return nil, ErrForbidden
	}
	input, err = normalize(input, s.now())
	if err != nil {
		return nil, err
	}
	event := &Event{OrganizerID: &userID, OrganizerName: audience.Nickname, OrganizerAvatarConfig: audience.AvatarConfig,
		EndsAt: input.EndsAt, Address: input.Address, EntryInfo: input.EntryInfo, CanManage: true, Poster: input.Poster, ID: uuid.NewString(), CityID: audience.CityID, Title: input.Title,
		Location: input.Location, Description: input.Description, StartsAt: input.StartsAt.UTC(), CreatedAt: s.now().UTC()}
	if err := s.repo.Create(userID, event); err != nil {
		return nil, err
	}
	return event, nil
}

func normalize(input Input, now time.Time) (Input, error) {
	input.Title = strings.TrimSpace(input.Title)
	input.Location = strings.TrimSpace(input.Location)
	input.Description = strings.TrimSpace(input.Description)
	if input.Title == "" || utf8.RuneCountInString(input.Title) > 120 ||
		input.Location == "" || utf8.RuneCountInString(input.Location) > 200 ||
		utf8.RuneCountInString(input.Description) > 10000 || input.StartsAt.IsZero() || input.StartsAt.Year() > 9999 {
		return input, ErrInvalid
	}
	if input.Poster != nil {
		p := input.Poster
		if p.Type != "photo" || p.MIMEType != "image/jpeg" || p.Width < 1 || p.Height < 1 || p.Width > 2048 || p.Height > 2048 {
			return input, ErrInvalid
		}
		input.Poster = &models.ImageMeta{URL: p.URL, Width: p.Width, Height: p.Height, Type: "photo", MIMEType: "image/jpeg"}
	}
	input.Address = strings.TrimSpace(input.Address)
	input.EntryInfo = strings.TrimSpace(input.EntryInfo)
	if utf8.RuneCountInString(input.Address) > 300 || utf8.RuneCountInString(input.EntryInfo) > 160 {
		return input, ErrInvalid
	}
	if input.EndsAt == nil {
		if !input.StartsAt.After(now) {
			return input, ErrInvalid
		}
	} else if !input.EndsAt.After(input.StartsAt) || !input.EndsAt.After(now) {
		return input, ErrInvalid
	}
	return input, nil
}

func (s *Service) Get(userID, id string) (*Event, error) {
	audience, err := s.repo.Audience(userID)
	if err != nil {
		return nil, err
	}
	if _, err := uuid.Parse(id); err != nil {
		return nil, ErrInvalid
	}
	event, err := s.repo.Get(audience.CityID, id)
	if err != nil {
		return nil, err
	}
	event.CanManage = audience.IsAdmin
	return event, nil
}
func (s *Service) Update(userID, id string, input Input) (*Event, error) {
	audience, err := s.repo.Audience(userID)
	if err != nil {
		return nil, err
	}
	if !audience.IsAdmin {
		return nil, ErrForbidden
	}
	if _, err := uuid.Parse(id); err != nil {
		return nil, ErrInvalid
	}
	input, err = normalize(input, s.now())
	if err != nil {
		return nil, err
	}
	if err := s.repo.Update(userID, audience.CityID, id, input); err != nil {
		return nil, err
	}
	return s.Get(userID, id)
}

func (s *Service) Delete(userID, id string) error {
	audience, err := s.repo.Audience(userID)
	if err != nil {
		return err
	}
	if !audience.IsAdmin {
		return ErrForbidden
	}
	if _, err := uuid.Parse(id); err != nil {
		return ErrInvalid
	}
	return s.repo.Delete(audience.CityID, id)
}

type Handler struct{ service *Service }

func NewHandler(service *Service) *Handler { return &Handler{service: service} }
func (h *Handler) RegisterRoutes(router fiber.Router) {
	router.Get("/events", h.list)
	router.Post("/events", h.create)
	router.Get("/events/:id", h.get)
	router.Patch("/events/:id", h.update)
	router.Delete("/events/:id", h.delete)
}
func (h *Handler) list(c *fiber.Ctx) error {
	page, err := h.service.List(c.Locals("user_id").(string), c.Query("cursor"))
	if err != nil {
		return respondError(c, err)
	}
	return c.JSON(page)
}
func (h *Handler) create(c *fiber.Ctx) error {
	var input Input
	if err := c.BodyParser(&input); err != nil {
		return respondError(c, ErrInvalid)
	}
	event, err := h.service.Create(c.Locals("user_id").(string), input)
	if err != nil {
		return respondError(c, err)
	}
	return c.Status(fiber.StatusCreated).JSON(event)
}
func (h *Handler) get(c *fiber.Ctx) error {
	event, err := h.service.Get(c.Locals("user_id").(string), c.Params("id"))
	if err != nil {
		return respondError(c, err)
	}
	return c.JSON(event)
}
func (h *Handler) update(c *fiber.Ctx) error {
	var input Input
	if err := c.BodyParser(&input); err != nil {
		return respondError(c, ErrInvalid)
	}
	event, err := h.service.Update(c.Locals("user_id").(string), c.Params("id"), input)
	if err != nil {
		return respondError(c, err)
	}
	return c.JSON(event)
}
func (h *Handler) delete(c *fiber.Ctx) error {
	if err := h.service.Delete(c.Locals("user_id").(string), c.Params("id")); err != nil {
		return respondError(c, err)
	}
	return c.SendStatus(fiber.StatusNoContent)
}
func respondError(c *fiber.Ctx, err error) error {
	code := fiber.StatusInternalServerError
	message := "eventi non disponibili, riprova"
	switch {
	case errors.Is(err, ErrForbidden):
		code, message = fiber.StatusForbidden, err.Error()
	case errors.Is(err, ErrInvalid):
		code, message = fiber.StatusBadRequest, err.Error()
	case errors.Is(err, ErrNotFound):
		code, message = fiber.StatusNotFound, err.Error()
	default:
		return apierror.Respond(c, err)
	}
	return c.Status(code).JSON(fiber.Map{"error": message})
}
