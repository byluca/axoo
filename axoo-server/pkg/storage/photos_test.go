package storage

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestPhotoBucketProvisioningAndUpload(t *testing.T) {
	created := false
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer test-key" {
			t.Error("missing server credentials")
		}
		switch r.Method + " " + r.URL.Path {
		case "GET /storage/v1/bucket/post-images":
			if !created {
				w.WriteHeader(404)
			}
		case "POST /storage/v1/bucket":
			var body map[string]any
			json.NewDecoder(r.Body).Decode(&body)
			if body["public"] != true || body["id"] != "post-images" {
				t.Error("invalid bucket settings")
			}
			created = true
		case "POST /storage/v1/object/post-images/user/photo.jpg":
			if r.Header.Get("Content-Type") != "image/jpeg" {
				t.Error("incorrect MIME")
			}
		default:
			t.Errorf("unexpected request: %s %s", r.Method, r.URL.Path)
		}
	}))
	defer server.Close()
	store := NewSupabaseStorage(Config{URL: server.URL, ServiceRoleKey: "test-key"})
	if err := store.EnsurePhotoBucket(context.Background()); err != nil {
		t.Fatal(err)
	}
	if err := store.EnsurePhotoBucket(context.Background()); err != nil {
		t.Fatal(err)
	}
	url, err := store.PutPhoto(context.Background(), "user/photo.jpg", []byte("jpeg"))
	if err != nil || url != server.URL+"/storage/v1/object/public/post-images/user/photo.jpg" {
		t.Fatalf("url=%s error=%v", url, err)
	}
}

func TestDeletePhotoTargetsOneObject(t *testing.T) {
	called := false
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		called = true
		if r.Method != http.MethodDelete || r.URL.Path != "/storage/v1/object/post-images" {
			t.Errorf("unexpected deletion: %s %s", r.Method, r.URL.Path)
		}
		var body struct {
			Prefixes []string `json:"prefixes"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Fatal(err)
		}
		if len(body.Prefixes) != 1 || body.Prefixes[0] != "owner/photo.jpg" {
			t.Errorf("unexpected deletion targets: %v", body.Prefixes)
		}
	}))
	defer server.Close()
	store := NewSupabaseStorage(Config{URL: server.URL, ServiceRoleKey: "test-key"})
	if err := store.DeletePhoto(context.Background(), "owner/photo.jpg"); err != nil {
		t.Fatal(err)
	}
	if !called {
		t.Fatal("storage was not called")
	}
}
