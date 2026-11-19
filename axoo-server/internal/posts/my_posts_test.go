package posts

import (
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"axoo-server/internal/models"
	"github.com/gofiber/fiber/v2"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
)

type myPostsService struct {
	PostService
	user, cursor string
	limit        int
}

func (s *myPostsService) ListMyPosts(userID, cursor string, limit int) (*PaginatedPosts, error) {
	s.user, s.cursor, s.limit = userID, cursor, limit
	return &PaginatedPosts{Posts: []models.Post{}, Limit: limit}, nil
}

func TestMineRouteUsesAuthenticatedAccount(t *testing.T) {
	service := &myPostsService{}
	app := fiber.New()
	app.Use(func(c *fiber.Ctx) error { c.Locals("user_id", "authenticated-user"); return c.Next() })
	NewHandler(service).RegisterRoutes(app)
	response, err := app.Test(httptest.NewRequest("GET", "/posts/mine?user_id=someone-else&cursor=next&limit=10", nil))
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	if response.StatusCode != 200 || service.user != "authenticated-user" || service.cursor != "next" || service.limit != 10 {
		t.Fatalf("mine route used wrong account or pagination: status=%d service=%+v", response.StatusCode, service)
	}
}

type myPostsRepository struct {
	PostRepository
	filter     ListFilter
	user, city string
}

func (r *myPostsRepository) FindAll(city, user string, filter ListFilter) ([]models.Post, error) {
	r.city, r.user, r.filter = city, user, filter
	return []models.Post{}, nil
}

func TestMyPostsDoesNotRequireCurrentCityAndClampsLimit(t *testing.T) {
	repo := &myPostsRepository{}
	result, err := NewService(repo, nil, nil).ListMyPosts("me", "cursor", 1000)
	if err != nil {
		t.Fatal(err)
	}
	if !repo.filter.Mine || repo.filter.Order != "new" || repo.filter.Cursor != "cursor" || repo.filter.Limit != 20 || repo.user != "me" || repo.city != "" || result.Limit != 20 {
		t.Fatalf("incorrect personal posts filter: %+v", repo)
	}
}

func TestMyPostsSQLFiltersOwnerAndRemovedPostsAndUsesStableCursor(t *testing.T) {
	db, err := gorm.Open(postgres.Open("host=localhost user=test dbname=test sslmode=disable"), &gorm.Config{DryRun: true, DisableAutomaticPing: true})
	if err != nil {
		t.Fatal(err)
	}
	var sql string
	var vars []interface{}
	err = db.Callback().Query().After("gorm:query").Register("capture-posts", func(tx *gorm.DB) {
		sql = tx.Statement.SQL.String()
		vars = append([]interface{}{}, tx.Statement.Vars...)
	})
	if err != nil {
		t.Fatal(err)
	}
	cursor := time.Date(2026, 10, 16, 12, 0, 0, 0, time.UTC).Format(time.RFC3339Nano) + "|00000000-0000-0000-0000-000000000001"
	_, err = NewRepository(db).FindAll("old-city", "owner", ListFilter{Mine: true, Order: "new", Cursor: cursor, Limit: 20})
	if err != nil {
		t.Fatal(err)
	}
	for _, required := range []string{"posts.user_id =", "posts.is_deleted = false", "posts.is_hidden = false", "created_at <", "created_at =", "id <", "ORDER BY posts.created_at DESC, posts.id DESC", "LIMIT"} {
		if !strings.Contains(sql, required) {
			t.Fatalf("query missing %q: %s", required, sql)
		}
	}
	if strings.Contains(sql, "posts.city_id =") || len(vars) == 0 || vars[0] != "owner" {
		t.Fatalf("query must use the owner across cities: %s %+v", sql, vars)
	}
}
