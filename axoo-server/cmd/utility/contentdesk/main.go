// Command contentdesk starts a private, loopback-only dashboard for publishing
// Axoo content with managed personas. It intentionally reuses the production
// services so validation, replies, notifications and votes behave like the app.
package main

import (
	"context"
	"crypto/rand"
	"embed"
	"encoding/base64"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io/fs"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"regexp"
	"strings"
	"syscall"
	"time"

	"axoo-server/internal/analytics"
	"axoo-server/internal/comments"
	"axoo-server/internal/models"
	"axoo-server/internal/posts"
	"axoo-server/internal/push"
	"axoo-server/internal/users"
	"axoo-server/pkg/database"

	"github.com/google/uuid"
	"github.com/joho/godotenv"
	"gorm.io/gorm"
)

const (
	address       = "127.0.0.1:8787"
	sessionCookie = "axoo_contentdesk"
	maxBodyBytes  = 64 * 1024
)

var nicknamePattern = regexp.MustCompile(`^[a-z0-9]{3,24}$`)

//go:embed web/*
var webFiles embed.FS

type desk struct {
	db               *gorm.DB
	postService      *posts.Service
	commentService   *comments.Service
	analyticsService *analytics.Service
	profiles         managedProfiles
	sessionToken     string
	static           http.Handler
	emojis           *emojiCatalog
}

type managedProfiles struct{ db *gorm.DB }

type profile struct {
	Gender         string `json:"gender,omitempty"`
	ID             string `json:"id"`
	Nickname       string `json:"nickname"`
	UniversityID   string `json:"university_id"`
	UniversityName string `json:"university_name"`
}

type university struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

type feedPost struct {
	models.Post
	Comments []models.Comment `json:"comments"`
}

type bootstrapResponse struct {
	Profiles     []profile    `json:"profiles"`
	Universities []university `json:"universities"`
	Posts        []feedPost   `json:"posts"`
	NextCursor   string       `json:"next_cursor"`
}

func main() {
	emojiDir := flag.String("emoji-assets", "../axoo-kotlin/app/src/main/assets/axooji", "cartella Axooji dell'app Android (catalog.json e animations)")
	profileCount := flag.Int("add-profiles", 0, "crea profili gestiti e termina (1-300)")
	profileGender := flag.String("profile-gender", "", "categoria dei nuovi profili: male o female")
	profileCampus := flag.String("profile-campus", "", "UUID del campus dei nuovi profili")
	renameCount := flag.Int("rename-profiles", 0, "rinomina i profili gestiti creati dalla data indicata e termina (1-300)")
	repairCount := flag.Int("repair-profile-nicknames", 0, "corregge solo i nickname non conformi al client nel gruppo indicato (1-300)")
	renameSince := flag.String("rename-profiles-since", "", "data RFC3339 di creazione minima dei profili da rinominare")
	flag.Parse()
	if (*renameCount != 0 && *profileCount != 0) || (*repairCount != 0 && (*renameCount != 0 || *profileCount != 0)) {
		log.Fatal("scegli creazione oppure rinomina profili")
	}
	emojis, err := loadEmojiCatalog(*emojiDir)
	if err != nil {
		log.Fatalf("Impossibile caricare Axooji: %v. Usa -emoji-assets per indicare la cartella dell'app Android.", err)
	}
	_ = godotenv.Load()
	database.Connect()
	if *renameCount != 0 || *repairCount != 0 {
		since, err := time.Parse(time.RFC3339, *renameSince)
		if err != nil {
			log.Fatal("indica -rename-profiles-since in formato RFC3339")
		}
		count := *renameCount
		if *repairCount != 0 {
			count = *repairCount
		}
		changes, err := (managedProfiles{db: database.DB}).changeProfileNicknames(count, since, *repairCount != 0)
		if err != nil {
			log.Fatal(err)
		}
		for _, p := range changes {
			log.Printf("Profilo rinominato: %s @%s -> @%s", p.ID, p.OldNickname, p.Nickname)
		}
		return
	}
	if *profileCount != 0 {
		created, err := (managedProfiles{db: database.DB}).addProfiles(*profileCount, *profileCampus, *profileGender)
		if err != nil {
			log.Fatal(err)
		}
		for _, p := range created {
			log.Printf("Profilo creato: @%s · %s", p.Nickname, p.UniversityName)
		}
		return
	}

	userRepo := users.NewRepository(database.DB)
	postRepo := posts.NewRepository(database.DB)
	commentRepo := comments.NewRepository(database.DB)
	credentials := os.Getenv("FCM_SERVICE_ACCOUNT_JSON")
	if path := os.Getenv("FCM_SERVICE_ACCOUNT_PATH"); credentials == "" && path != "" {
		data, err := os.ReadFile(path)
		if err != nil {
			log.Fatalf("Impossibile leggere le credenziali FCM: %v", err)
		}
		credentials = string(data)
	}
	var sender push.Sender
	if projectID := os.Getenv("FCM_PROJECT_ID"); credentials != "" || projectID != "" {
		sender, err = push.NewFCMSender(context.Background(), projectID, credentials)
		if err != nil {
			log.Fatalf("Impossibile configurare le notifiche: %v", err)
		}
	}
	pushService := push.NewService(push.NewRepository(database.DB), sender)

	staticFS, err := fs.Sub(webFiles, "web")
	if err != nil {
		log.Fatal(err)
	}
	d := &desk{
		db:               database.DB,
		postService:      posts.NewService(postRepo, posts.NewDBAvatarConfigLoader(database.DB), userRepo),
		commentService:   comments.NewService(commentRepo, pushService),
		analyticsService: analytics.NewService(analytics.NewRepository(database.DB)),
		profiles:         managedProfiles{db: database.DB},
		sessionToken:     randomToken(),
		static:           http.FileServer(http.FS(staticFS)),
		emojis:           emojis,
	}

	listener, err := net.Listen("tcp", address)
	if err != nil {
		log.Fatal(err)
	}
	defer listener.Close()
	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	mux := http.NewServeMux()
	mux.HandleFunc("GET /open", d.open)
	mux.HandleFunc("GET /api/bootstrap", d.authorized(d.bootstrap))
	mux.HandleFunc("GET /api/analytics", d.authorized(d.analyticsOverview))
	mux.HandleFunc("GET /api/emojis", d.authorized(d.emojiList))
	mux.HandleFunc("GET /api/emoji-preview", d.authorized(d.emojiPreview))
	mux.HandleFunc("POST /api/profiles", d.authorized(d.createProfile))
	mux.HandleFunc("POST /api/profiles/bulk", d.authorized(d.createProfiles))
	mux.HandleFunc("POST /api/posts", d.authorized(d.createPost))
	mux.HandleFunc("GET /api/posts", d.authorized(d.listManualPosts))
	mux.HandleFunc("GET /api/posts/{id}/comments", d.authorized(d.listManualComments))
	mux.HandleFunc("POST /api/comments", d.authorized(d.createComment))
	mux.HandleFunc("PUT /api/posts/vote", d.authorized(d.votePost))
	mux.HandleFunc("PUT /api/posts/poll/vote", d.authorized(d.votePoll))
	mux.HandleFunc("PUT /api/comments/vote", d.authorized(d.voteComment))
	mux.Handle("/", d.authorized(func(w http.ResponseWriter, r *http.Request) { d.static.ServeHTTP(w, r) }))

	server := &http.Server{
		Addr:              address,
		Handler:           securityHeaders(limitBody(mux)),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	go func() {
		<-ctx.Done()
		_ = server.Close()
	}()

	url := "http://" + address + "/open?token=" + d.sessionToken
	log.Printf("Axoo Content Desk pronto: %s", url)
	log.Printf("Solo questo PC può collegarsi. Premi Ctrl+C per chiudere.")
	if err := server.Serve(listener); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatal(err)
	}
}
func randomToken() string {

	data := make([]byte, 32)
	if _, err := rand.Read(data); err != nil {
		log.Fatal(err)
	}
	return base64.RawURLEncoding.EncodeToString(data)
}

func (d *desk) open(w http.ResponseWriter, r *http.Request) {
	if r.URL.Query().Get("token") != d.sessionToken {
		http.Error(w, "accesso negato", http.StatusForbidden)
		return
	}
	d.setSessionCookie(w)
	http.Redirect(w, r, "/", http.StatusSeeOther)
}

func (d *desk) setSessionCookie(w http.ResponseWriter) {
	http.SetCookie(w, &http.Cookie{
		Name: d.sessionCookieName(), Value: d.sessionToken, Path: "/",
		HttpOnly: true, SameSite: http.SameSiteStrictMode,
	})
}

func (d *desk) sessionCookieName() string { return sessionCookie }

func (d *desk) authorized(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		cookie, err := r.Cookie(d.sessionCookieName())
		if err != nil || cookie.Value != d.sessionToken {
			if strings.HasPrefix(r.URL.Path, "/api/") {
				writeError(w, http.StatusUnauthorized, "sessione Content Desk non valida")
			} else {
				http.Error(w, "Apri il link completo stampato nel terminale.", http.StatusUnauthorized)
			}
			return
		}
		next(w, r)
	}
}

func (d *desk) bootstrap(w http.ResponseWriter, r *http.Request) {
	profiles, err := d.profiles.list()
	if err != nil {
		writeInternalError(w, err)
		return
	}
	universities, err := d.profiles.universities()
	if err != nil {
		writeInternalError(w, err)
		return
	}

	actorID := r.URL.Query().Get("actor")
	if actorID == "" && len(profiles) > 0 {
		actorID = profiles[0].ID
	}
	result := bootstrapResponse{Profiles: profiles, Universities: universities, Posts: []feedPost{}}
	if actorID == "" {
		writeJSON(w, http.StatusOK, result)
		return
	}
	if err := d.profiles.require(actorID); err != nil {
		writeError(w, http.StatusForbidden, err.Error())
		return
	}
	page, err := d.postService.ListPosts(actorID, "new", "", 15, 0)
	if err != nil {
		writeInternalError(w, err)
		return
	}
	for _, post := range page.Posts {
		result.Posts = append(result.Posts, feedPost{Post: post, Comments: []models.Comment{}})
	}
	result.NextCursor = page.NextCursor
	writeJSON(w, http.StatusOK, result)
}

func (d *desk) createProfile(w http.ResponseWriter, r *http.Request) {
	var request struct {
		Gender       string `json:"gender"`
		Nickname     string `json:"nickname"`
		UniversityID string `json:"university_id"`
	}
	if !decodeJSON(w, r, &request) {
		return
	}
	request.Nickname = strings.TrimSpace(request.Nickname)
	if !nicknamePattern.MatchString(request.Nickname) || !validProfileGender(request.Gender) {
		writeError(w, http.StatusBadRequest, "nickname: usa 3-24 caratteri, solo lettere minuscole e numeri")
		return
	}
	created, err := d.profiles.create(request.Nickname, request.UniversityID, request.Gender)
	if err != nil {
		if isConflict(err) {
			writeError(w, http.StatusConflict, "nickname già utilizzato")
		} else {
			writeInternalError(w, err)
		}
		return
	}
	writeJSON(w, http.StatusCreated, created)
}

func (d *desk) createPost(w http.ResponseWriter, r *http.Request) {
	var request struct {
		Poll     *models.PollInput `json:"poll"`
		ActorID  string            `json:"actor_id"`
		Body     string            `json:"body"`
		Flair    string            `json:"flair"`
		MediaURL string            `json:"media_url"`
		ClientID string            `json:"client_id"`
	}
	if !decodeJSON(w, r, &request) || !d.requireActor(w, request.ActorID) {
		return
	}
	if request.ClientID == "" {
		request.ClientID = uuid.NewString()
	}
	if _, err := uuid.Parse(request.ClientID); err != nil {
		writeError(w, http.StatusBadRequest, "identificativo invio non valido")
		return
	}
	var media *models.ImageMeta
	if value := strings.TrimSpace(request.MediaURL); value != "" {
		media = &models.ImageMeta{URL: value, Width: 1, Height: 1, Type: "meme", MIMEType: "image/jpeg"}
	}
	body, entities, err := d.emojis.expand(request.Body)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	if request.Poll != nil {
		for i := range request.Poll.Options {
			text, optionEntities, err := d.emojis.expand(request.Poll.Options[i].Text)
			if err != nil {
				writeError(w, http.StatusBadRequest, err.Error())
				return
			}
			request.Poll.Options[i].Text = text
			request.Poll.Options[i].Entities = optionEntities
		}
	}
	post, _, err := d.postService.CreatePost(request.ActorID, request.ClientID, body, request.Flair, media, entities, request.Poll)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	writeJSON(w, http.StatusCreated, post)
}

func (d *desk) createComment(w http.ResponseWriter, r *http.Request) {
	var request struct {
		ActorID          string `json:"actor_id"`
		PostID           string `json:"post_id"`
		Body             string `json:"body"`
		ReplyToCommentID string `json:"reply_to_comment_id"`
		ClientID         string `json:"client_id"`
	}
	if !decodeJSON(w, r, &request) || !d.requireActor(w, request.ActorID) {
		return
	}
	if request.ClientID == "" {
		request.ClientID = uuid.NewString()
	}
	if _, err := uuid.Parse(request.ClientID); err != nil {
		writeError(w, http.StatusBadRequest, "identificativo invio non valido")
		return
	}
	body, entities, err := d.emojis.expand(request.Body)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	comment, _, err := d.commentService.CreateComment(
		request.ActorID, request.PostID, request.ClientID, body, entities, request.ReplyToCommentID,
		nil,
	)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	writeJSON(w, http.StatusCreated, comment)
}

func (d *desk) votePost(w http.ResponseWriter, r *http.Request) {
	var request struct {
		ActorID string `json:"actor_id"`
		PostID  string `json:"post_id"`
		Value   int    `json:"value"`
	}
	if !decodeJSON(w, r, &request) || !d.requireActor(w, request.ActorID) {
		return
	}
	result, err := d.postService.VotePost(request.PostID, request.ActorID, request.Value)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, result)
}

func (d *desk) voteComment(w http.ResponseWriter, r *http.Request) {
	var request struct {
		ActorID   string `json:"actor_id"`
		CommentID string `json:"comment_id"`
		Value     int    `json:"value"`
	}
	if !decodeJSON(w, r, &request) || !d.requireActor(w, request.ActorID) {
		return
	}
	result, err := d.commentService.VoteComment(request.CommentID, request.ActorID, request.Value)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, result)
}

func (d *desk) requireActor(w http.ResponseWriter, actorID string) bool {
	if err := d.profiles.require(actorID); err != nil {
		writeError(w, http.StatusForbidden, err.Error())
		return false
	}
	return true
}

func (m managedProfiles) list() ([]profile, error) {
	result := make([]profile, 0)
	err := m.db.Table("users AS u").
		Select(`u.id, u.nickname, u.university_id, universities.name AS university_name,
            CASE WHEN u.email_hash LIKE 'contentdesk-male-%' THEN 'male'
                 WHEN u.email_hash LIKE 'contentdesk-female-%' THEN 'female' ELSE '' END AS gender`).
		Joins("JOIN universities ON universities.id = u.university_id").
		Where("u.email_hash LIKE ? OR u.email_hash LIKE ? OR u.email_hash LIKE ?", "contentdesk-%", "seed-%", "seed-network-%").
		Order("u.nickname").Scan(&result).Error
	return result, err
}

func (m managedProfiles) universities() ([]university, error) {
	result := make([]university, 0)
	err := m.db.Table("universities").Select("id, name").Order("name").Scan(&result).Error
	return result, err
}

func (m managedProfiles) require(id string) error {
	if _, err := uuid.Parse(id); err != nil {
		return errors.New("profilo non valido")
	}
	var count int64
	err := m.db.Model(&models.User{}).
		Where("id = ? AND (email_hash LIKE ? OR email_hash LIKE ? OR email_hash LIKE ?)", id, "contentdesk-%", "seed-%", "seed-network-%").
		Count(&count).Error
	if err != nil {
		return err
	}
	if count != 1 {
		return errors.New("il profilo non è gestito dal Content Desk")
	}
	return nil
}

func (m managedProfiles) create(nickname, universityID, gender string) (*profile, error) {
	if !validProfileGender(gender) {
		return nil, errors.New("categoria profilo non valida")
	}
	if _, err := uuid.Parse(universityID); err != nil {
		return nil, errors.New("università non valida")
	}
	var universityName string
	if err := m.db.Table("universities").Select("name").Where("id = ?", universityID).Scan(&universityName).Error; err != nil {
		return nil, err
	}
	if universityName == "" {
		return nil, errors.New("università non trovata")
	}
	profileUUID := uuid.New()
	id := profileUUID.String()
	avatar := generatedAvatar(profileUUID)
	user := newContentDeskUser(id, nickname, universityID, avatar)
	if gender != "" {
		user.EmailHash = "contentdesk-" + gender + "-" + id
	}
	if err := m.db.Create(&user).Error; err != nil {
		return nil, err
	}
	return &profile{ID: id, Nickname: nickname, UniversityID: universityID, UniversityName: universityName, Gender: gender}, nil
}

func newContentDeskUser(id, nickname, universityID string, avatar models.AvatarConfig) models.User {
	return models.User{
		ID: id, Nickname: nickname, UniversityID: universityID,
		EmailHash: "contentdesk-" + id, AvatarConfig: avatar, IsAlphaTester: true,
	}
}

func generatedAvatar(seed uuid.UUID) models.AvatarConfig {
	return defaultAxooAvatarConfig(seed.String())
}

// Keep this generator in lockstep with axoo-kotlin's defaultAvatarParams.
// Content Desk deliberately stores only values accepted by the mobile editor.
var (
	axooAvatarHair = []string{
		"long01", "long02", "long03", "long04", "long05", "long07", "long08", "long09",
		"long10", "long11", "long12", "long13", "long14", "long15", "long16",
		"long19", "long20", "long21", "long23", "long24", "long25", "long26",
		"short01", "short02", "short03", "short04", "short05", "short06", "short07", "short08",
		"short09", "short10", "short11", "short12", "short13", "short14", "short15", "short16",
		"short17", "short18", "short19",
	}
	axooAvatarHairColors  = []string{"0e0e0e", "6a4e35", "562306", "cb6820", "b9a05f", "e5d7a3", "ac6511", "796a45", "ab2a18", "afafaf", "dba3be", "85c2c6", "3eac2c", "592454"}
	axooAvatarEyes        = []string{"variant01", "variant02", "variant03", "variant05", "variant12", "variant14", "variant17", "variant20", "variant26", "variant04", "variant06", "variant07", "variant11", "variant13", "variant18", "variant19", "variant21", "variant22", "variant23", "variant24", "variant25"}
	axooAvatarEyebrows    = []string{"variant01", "variant02", "variant06", "variant07", "variant10", "variant12", "variant03", "variant05", "variant08", "variant09", "variant11", "variant13", "variant14", "variant15"}
	axooAvatarMouths      = []string{"variant01", "variant02", "variant12", "variant22", "variant24", "variant25", "variant30", "variant15", "variant16", "variant17", "variant19", "variant21", "variant23", "variant26", "variant27", "variant28", "variant29"}
	axooAvatarGlasses     = []string{"variant01", "variant02", "variant03", "variant04", "variant05"}
	axooAvatarEarrings    = []string{"variant01", "variant02", "variant03", "variant04", "variant05", "variant06"}
	axooAvatarDetails     = []string{"blush", "freckles", "birthmark", "mustache"}
	axooAvatarSkinColors  = []string{"f2d3b1", "ecad80", "9e5622", "763900"}
	axooAvatarBackgrounds = []string{
		"ff8fab", "ffb703", "4cc9a7", "4d96ff", "b57bff",
		"ff6b35", "00b4d8", "8ac926", "e63946", "6a4c93",
	}
)

func defaultAxooAvatarConfig(seed string) models.AvatarConfig {
	pick := func(parameter string, values []string) string {
		return values[axooAvatarIndex(seed, parameter, len(values))]
	}
	avatar := models.AvatarConfig{
		"hairVariant":     pick("hairVariant", axooAvatarHair),
		"hairColor":       pick("hairColor", axooAvatarHairColors),
		"backgroundColor": pick("backgroundColor", axooAvatarBackgrounds),
		"eyesVariant":     pick("eyesVariant", axooAvatarEyes),
		"eyebrowsVariant": pick("eyebrowsVariant", axooAvatarEyebrows),
		"mouthVariant":    pick("mouthVariant", axooAvatarMouths),
		// Axoo's default avatar starts with the first supported skin tone; users
		// can choose another supported value in the avatar editor.
		"skinColor": axooAvatarSkinColors[0],
	}
	if axooAvatarChance(seed, "earringsVariant", 8) {
		avatar["earringsVariant"] = pick("earringsPick", axooAvatarEarrings)
	}
	if axooAvatarChance(seed, "glassesVariant", 5) {
		avatar["glassesVariant"] = pick("glassesPick", axooAvatarGlasses)
	}
	if axooAvatarChance(seed, "detailsVariant", 10) {
		avatar["detailsVariant"] = pick("detailsPick", axooAvatarDetails)
	}
	return avatar
}

func axooAvatarIndex(seed, parameter string, size int) int {
	hash := 0
	for _, character := range seed + parameter {
		hash = (hash*31 + int(character)) & 0x7fffffff
	}
	return hash % size
}

func axooAvatarChance(seed, parameter string, percent int) bool {
	return axooAvatarIndex(seed, parameter, 100) < percent
}

func decodeJSON(w http.ResponseWriter, r *http.Request, target any) bool {
	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		writeError(w, http.StatusBadRequest, "dati non validi")
		return false
	}
	return true
}

func isConflict(err error) bool {
	value := strings.ToLower(err.Error())
	return strings.Contains(value, "duplicate key") || strings.Contains(value, "unique constraint")
}

func writeInternalError(w http.ResponseWriter, err error) {
	log.Printf("contentdesk: %v", err)
	writeError(w, http.StatusInternalServerError, "operazione non riuscita")
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func limitBody(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Body != nil {
			r.Body = http.MaxBytesReader(w, r.Body, maxBodyBytes)
		}
		next.ServeHTTP(w, r)
	})
}

func securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("Content-Security-Policy", "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' https: data:; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'")
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("X-Frame-Options", "DENY")
		next.ServeHTTP(w, r)
	})
}

func init() {
	if !strings.HasPrefix(address, "127.0.0.1:") {
		panic(fmt.Sprintf("contentdesk must bind to loopback, got %s", address))
	}
}
