package events

import (
	"axoo-server/internal/models"
	"errors"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
)

type fakeRepository struct {
	admin   bool
	items   []Event
	created *Event
	deleted bool
	city    string
	cursor  *Cursor
	err     error
}

func (r *fakeRepository) Audience(string) (Audience, error) {
	return Audience{CityID: "my-city", IsAdmin: r.admin}, r.err
}
func (r *fakeRepository) List(city string, _ time.Time, cursor *Cursor, _ int) ([]Event, error) {
	r.city, r.cursor = city, cursor
	return r.items, r.err
}
func (r *fakeRepository) Create(_ string, e *Event) error { r.created = e; return r.err }
func (r *fakeRepository) Delete(city, _ string) error     { r.city, r.deleted = city, true; return r.err }

func (r *fakeRepository) Get(city, id string) (*Event, error) {
	r.city = city
	if r.err != nil {
		return nil, r.err
	}
	return &Event{ID: id}, nil
}
func (r *fakeRepository) Update(_, city, _ string, _ Input) error { r.city = city; return r.err }

func TestOnlyAdminCanWrite(t *testing.T) {
	for _, role := range []string{"student", "moderator", "legacy-organizer"} {
		t.Run(role, func(t *testing.T) {
			r := &fakeRepository{}
			s := NewService(r)
			if _, err := s.Create("me", Input{}); !errors.Is(err, ErrForbidden) {
				t.Fatalf("create: %v", err)
			}
			if err := s.Delete("me", uuid.NewString()); !errors.Is(err, ErrForbidden) {
				t.Fatalf("delete: %v", err)
			}
			if _, err := s.Update("me", uuid.NewString(), Input{}); !errors.Is(err, ErrForbidden) {
				t.Fatalf("update: %v", err)
			}
			if r.created != nil || r.deleted {
				t.Fatal("unauthorized write")
			}
			page, err := s.List("me", "")
			if err != nil || page.CanManage || page.Events == nil || r.city != "my-city" {
				t.Fatalf("student read: %+v %v", page, err)
			}
		})
	}
}

func TestCreateValidatesAndDerivesCity(t *testing.T) {
	r := &fakeRepository{admin: true}
	s := NewService(r)
	now := time.Date(2026, 11, 16, 12, 0, 0, 0, time.UTC)
	s.now = func() time.Time { return now }
	input := Input{Title: "  Aperitivo  ", Location: "  Campus  ", Description: "  Ingresso libero  ", StartsAt: now.Add(time.Hour)}
	event, err := s.Create("me", input)
	if err != nil || event.CityID != "my-city" || event.Title != "Aperitivo" || event.Description != "Ingresso libero" {
		t.Fatalf("%+v %v", event, err)
	}
	if _, err := uuid.Parse(event.ID); err != nil {
		t.Fatal(err)
	}
	for _, change := range []func(*Input){
		func(i *Input) { i.Title = " " }, func(i *Input) { i.Location = " " },
		func(i *Input) { i.Title = strings.Repeat("😀", 121) }, func(i *Input) { i.Location = strings.Repeat("a", 201) },
		func(i *Input) { i.Description = strings.Repeat("a", 10001) },
		func(i *Input) { i.StartsAt = now }, func(i *Input) { i.StartsAt = time.Time{} },
	} {
		bad := input
		change(&bad)
		if _, err := s.Create("me", bad); !errors.Is(err, ErrInvalid) {
			t.Fatalf("accepted %+v: %v", bad, err)
		}
	}
	input.Title = strings.Repeat("😀", 120)
	if _, err := s.Create("me", input); err != nil {
		t.Fatal("unicode title rejected", err)
	}
	if err := s.Delete("me", uuid.NewString()); err != nil || r.city != "my-city" {
		t.Fatalf("delete: %v", err)
	}
}

func TestPaginationAndBadCursors(t *testing.T) {
	r := &fakeRepository{admin: true}
	for i := 0; i < 21; i++ {
		r.items = append(r.items, Event{ID: uuid.NewString(), StartsAt: time.Now().Add(time.Hour)})
	}
	s := NewService(r)
	page, err := s.List("me", "")
	if err != nil || len(page.Events) != 20 || page.NextCursor == "" || !page.CanManage {
		t.Fatalf("%+v %v", page, err)
	}
	if _, err := s.List("me", page.NextCursor); err != nil || r.cursor.ID != page.Events[19].ID {
		t.Fatalf("cursor %+v %v", r.cursor, err)
	}
	for _, raw := range []string{"!", "e30", "bnVsbA"} {
		if _, err := s.List("me", raw); !errors.Is(err, ErrInvalid) {
			t.Fatalf("bad cursor %s: %v", raw, err)
		}
	}
}

func TestHTTPPermissionsAndErrors(t *testing.T) {
	r := &fakeRepository{}
	app := fiber.New()
	app.Use(func(c *fiber.Ctx) error { c.Locals("user_id", "me"); return c.Next() })
	NewHandler(NewService(r)).RegisterRoutes(app)
	for _, tc := range []struct {
		method, path, body string
		code               int
	}{
		{"GET", "/events", "", 200},
		{"GET", "/events/" + uuid.NewString(), "", 200},
		{"GET", "/events/bad", "", 400},
		{"PATCH", "/events/" + uuid.NewString(), `{}`, 403},
		{"POST", "/events", `{}`, 403},
		{"DELETE", "/events/" + uuid.NewString(), "", 403},
		{"POST", "/events", `{"starts_at":"bad"}`, 400},
		{"GET", "/events?cursor=bad", "", 400},
	} {
		req := httptest.NewRequest(tc.method, tc.path, strings.NewReader(tc.body))
		req.Header.Set("Content-Type", "application/json")
		resp, err := app.Test(req)
		if err != nil {
			t.Fatal(err)
		}
		resp.Body.Close()
		if resp.StatusCode != tc.code {
			t.Fatalf("%s %s: %d, want %d", tc.method, tc.path, resp.StatusCode, tc.code)
		}
	}
	r.admin = true
	r.err = errors.New("private database details")
	resp, err := app.Test(httptest.NewRequest("GET", "/events", nil))
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != 500 {
		t.Fatal(resp.StatusCode)
	}
}

func TestPosterValidation(t *testing.T) {
	r := &fakeRepository{admin: true}
	s := NewService(r)
	input := Input{Title: "Evento", Location: "Campus", StartsAt: time.Now().Add(time.Hour), Poster: &models.ImageMeta{URL: "https://example.test/poster.jpg", Width: 800, Height: 1200, Type: "photo", MIMEType: "image/jpeg"}}
	e, err := s.Create("me", input)
	if err != nil || e.Poster == nil || e.Poster.Width != 800 {
		t.Fatalf("poster: %+v %v", e, err)
	}
	original := *input.Poster
	for _, change := range []func(*models.ImageMeta){func(p *models.ImageMeta) { p.Type = "gif" }, func(p *models.ImageMeta) { p.MIMEType = "image/png" }, func(p *models.ImageMeta) { p.Width = 0 }, func(p *models.ImageMeta) { p.Height = 2049 }} {
		bad := original
		change(&bad)
		input.Poster = &bad
		if _, err := s.Create("me", input); !errors.Is(err, ErrInvalid) {
			t.Fatalf("accepted invalid poster: %v", err)
		}
	}
}

func TestOriginalEditorFieldsAndDates(t *testing.T) {
	now := time.Now().UTC()
	start, end := now.Add(-time.Hour), now.Add(time.Hour)
	valid := Input{Title: " Evento ", Location: " Locale ", StartsAt: start, EndsAt: &end, Address: " Via Roma ", EntryInfo: " Gratuito "}
	got, err := normalize(valid, now)
	if err != nil || got.Address != "Via Roma" || got.EntryInfo != "Gratuito" {
		t.Fatalf("valid ongoing event: %+v %v", got, err)
	}
	for _, change := range []func(*Input){
		func(i *Input) { i.EndsAt = &start },
		func(i *Input) { past := now.Add(-time.Minute); i.EndsAt = &past },
		func(i *Input) { i.Address = strings.Repeat("a", 301) },
		func(i *Input) { i.EntryInfo = strings.Repeat("a", 161) },
	} {
		bad := valid
		change(&bad)
		if _, err := normalize(bad, now); !errors.Is(err, ErrInvalid) {
			t.Fatalf("invalid original editor fields accepted: %v", err)
		}
	}
}
