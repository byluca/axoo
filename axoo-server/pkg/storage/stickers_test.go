package storage

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestStickerUploadUsesPNGAndImmutablePersonalPath(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" || r.URL.Path != "/storage/v1/object/memes/stickers/users/owner/id.png" ||
			r.Header.Get("Content-Type") != "image/png" || r.Header.Get("x-upsert") != "" {
			t.Error("invalid upload request")
		}
		raw, _ := io.ReadAll(r.Body)
		if string(raw) != "png" {
			t.Error("wrong body")
		}
		w.WriteHeader(201)
	}))
	defer server.Close()
	store := NewSupabaseStorage(Config{URL: server.URL, ServiceRoleKey: "test-only"})
	url, err := store.PutSticker(context.Background(), "stickers/users/owner/id.png", []byte("png"))
	if err != nil || url != server.URL+"/storage/v1/object/public/memes/stickers/users/owner/id.png" {
		t.Fatalf("wrong URL: %q %v", url, err)
	}
}

func TestStickerCopyStaysInSameBucket(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var request map[string]string
		json.NewDecoder(r.Body).Decode(&request)
		if r.URL.Path != "/storage/v1/object/copy" || r.Method != "POST" || request["bucketId"] != "memes" ||
			request["sourceKey"] != "stickers/users/first/id.png" || request["destinationKey"] != "stickers/users/second/id.png" {
			t.Error("invalid copy request")
		}
		w.WriteHeader(200)
	}))
	defer server.Close()
	store := NewSupabaseStorage(Config{URL: server.URL, ServiceRoleKey: "test-only"})
	if err := store.CopySticker(context.Background(), "stickers/users/first/id.png", "stickers/users/second/id.png"); err != nil {
		t.Fatal(err)
	}
}
