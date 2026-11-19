package main

import (
	"axoo-server/internal/models"
	"embed"
	"encoding/json"
	"fmt"
	"io/fs"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"unicode/utf8"
)

type deskEmoji struct {
	ID           int    `json:"id"`
	Name         string `json:"name"`
	Fallback     string `json:"fallback"`
	PickerHidden bool   `json:"pickerHidden,omitempty"`
}

type emojiCategory struct {
	ID    string   `json:"id"`
	Label string   `json:"label"`
	Items []string `json:"items"`
}

type emojiCatalog struct {
	SchemaVersion int             `json:"schemaVersion"`
	Emojis        []deskEmoji     `json:"emojis"`
	Categories    []emojiCategory `json:"categories"`
	byName        map[string]deskEmoji
}

// Keep the Desk's static previews independent of the Android asset layout.
//
//go:embed emoji-previews/*/*.webp
var emojiPreviewFiles embed.FS

var emojiNamePattern = regexp.MustCompile(`^[a-z0-9]+(?:-[a-z0-9]+)*/[a-z0-9]+(?:-[a-z0-9]+)*$`)
var emojiTokenPattern = regexp.MustCompile(`:([a-z0-9]+(?:-[a-z0-9]+)*/[a-z0-9]+(?:-[a-z0-9]+)*):`)

func loadEmojiCatalog(dir string) (*emojiCatalog, error) {
	raw, err := os.ReadFile(filepath.Join(dir, "catalog.json"))
	if err != nil {
		return nil, err
	}
	var catalog emojiCatalog
	if err := json.Unmarshal(raw, &catalog); err != nil {
		return nil, err
	}
	if catalog.SchemaVersion != 1 || len(catalog.Emojis) == 0 {
		return nil, fmt.Errorf("catalogo Axooji non valido")
	}
	catalog.byName = make(map[string]deskEmoji, len(catalog.Emojis))
	ids := make(map[int]bool, len(catalog.Emojis))
	for _, emoji := range catalog.Emojis {
		_, duplicateName := catalog.byName[emoji.Name]
		if !emojiNamePattern.MatchString(emoji.Name) || emoji.Fallback == "" || emoji.ID < 0 || ids[emoji.ID] || duplicateName {
			return nil, fmt.Errorf("emoji non valida o duplicata: %q", emoji.Name)
		}
		if _, err := os.Stat(filepath.Join(dir, "animations", emoji.Name+".tgs")); err != nil {
			return nil, err
		}
		if _, err := fs.Stat(emojiPreviewFiles, "emoji-previews/"+emoji.Name+".webp"); err != nil {
			return nil, fmt.Errorf("anteprima Axooji mancante per %s: %w; esegui python3 scripts/generate_emoji_previews.py", emoji.Name, err)
		}
		catalog.byName[emoji.Name] = emoji
		ids[emoji.ID] = true
	}
	for _, category := range catalog.Categories {
		for _, name := range category.Items {
			if _, ok := catalog.byName[name]; !ok {
				return nil, fmt.Errorf("emoji sconosciuta nella categoria: %s", name)
			}
		}
	}
	visibleCategories := catalog.Categories[:0]
	for i := range catalog.Categories {
		visible := make([]string, 0, len(catalog.Categories[i].Items))
		for _, name := range catalog.Categories[i].Items {
			if !catalog.byName[name].PickerHidden {
				visible = append(visible, name)
			}
		}
		catalog.Categories[i].Items = visible
		if len(visible) > 0 {
			visibleCategories = append(visibleCategories, catalog.Categories[i])
		}
	}
	catalog.Categories = visibleCategories
	return &catalog, nil
}

// API offsets are Unicode code points, not UTF-8 bytes or JavaScript UTF-16 units.
// Store the fallback in the body just like Android, so plain text stays readable.
func (catalog *emojiCatalog) expand(source string) (string, []models.MessageEntity, error) {
	var body strings.Builder
	var entities []models.MessageEntity
	position, offset := 0, 0
	for _, match := range emojiTokenPattern.FindAllStringSubmatchIndex(source, -1) {
		name := source[match[2]:match[3]]
		if catalog == nil {
			return "", nil, fmt.Errorf("catalogo emoji non disponibile")
		}
		emoji, ok := catalog.byName[name]
		if !ok {
			return "", nil, fmt.Errorf("emoji sconosciuta: %s", name)
		}
		prefix := source[position:match[0]]
		body.WriteString(prefix)
		offset += utf8.RuneCountInString(prefix)
		length := utf8.RuneCountInString(emoji.Fallback)
		entities = append(entities, models.MessageEntity{Type: "custom_emoji", Offset: offset, Length: length, CustomEmojiID: emoji.ID})
		body.WriteString(emoji.Fallback)
		offset += length
		position = match[1]
	}
	body.WriteString(source[position:])
	return body.String(), entities, nil
}

func (d *desk) emojiList(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, d.emojis)
}

func (d *desk) emojiPreview(w http.ResponseWriter, r *http.Request) {
	emoji, ok := d.emojis.byName[r.URL.Query().Get("name")]
	if !ok {
		http.NotFound(w, r)
		return
	}
	http.ServeFileFS(w, r, emojiPreviewFiles, "emoji-previews/"+emoji.Name+".webp")
}
