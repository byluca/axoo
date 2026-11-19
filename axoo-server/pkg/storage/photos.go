package storage

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"

	"axoo-server/internal/media"
)

func (s *SupabaseStorage) photoRequest(ctx context.Context, method, path, mime string, body []byte) (int, error) {
	req, err := http.NewRequestWithContext(ctx, method, strings.TrimRight(s.config.URL, "/")+"/storage/v1/"+path, bytes.NewReader(body))
	if err != nil {
		return 0, err
	}
	req.Header.Set("Authorization", "Bearer "+s.config.ServiceRoleKey)
	req.Header.Set("apikey", s.config.ServiceRoleKey)
	req.Header.Set("Content-Type", mime)
	response, err := http.DefaultClient.Do(req)
	if err != nil {
		return 0, err
	}
	defer response.Body.Close()
	io.Copy(io.Discard, io.LimitReader(response.Body, 4096))
	return response.StatusCode, nil
}

func (s *SupabaseStorage) EnsurePhotoBucket(ctx context.Context) error {
	status, err := s.photoRequest(ctx, "GET", "bucket/"+media.PhotoBucket, "application/json", nil)
	if err != nil {
		return err
	}
	if status == 200 {
		return nil
	}
	if status != 404 && status != 400 {
		return fmt.Errorf("photo bucket check: HTTP %d", status)
	}
	body, _ := json.Marshal(map[string]any{"id": media.PhotoBucket, "name": media.PhotoBucket, "public": true, "file_size_limit": media.MaxPhotoBytes, "allowed_mime_types": []string{"image/jpeg"}})
	status, err = s.photoRequest(ctx, "POST", "bucket", "application/json", body)
	if err != nil {
		return err
	}
	if status >= 200 && status < 300 {
		return nil
	}
	// Another instance may have created it concurrently.
	status, err = s.photoRequest(ctx, "GET", "bucket/"+media.PhotoBucket, "application/json", nil)
	if err != nil {
		return err
	}
	if status != 200 {
		return fmt.Errorf("photo bucket creation failed: HTTP %d", status)
	}
	return nil
}

func (s *SupabaseStorage) PutPhoto(ctx context.Context, path string, data []byte) (string, error) {
	status, err := s.photoRequest(ctx, "POST", "object/"+media.PhotoBucket+"/"+path, "image/jpeg", data)
	if err != nil {
		return "", err
	}
	if status < 200 || status >= 300 {
		return "", fmt.Errorf("photo upload: HTTP %d", status)
	}
	return strings.TrimRight(s.config.URL, "/") + "/storage/v1/object/public/" + media.PhotoBucket + "/" + path, nil
}

func (s *SupabaseStorage) DeletePhoto(ctx context.Context, path string) error {
	body, _ := json.Marshal(map[string]any{"prefixes": []string{path}})
	status, err := s.photoRequest(ctx, "DELETE", "object/"+media.PhotoBucket, "application/json", body)
	if err != nil {
		return err
	}
	if status < 200 || status >= 300 {
		return fmt.Errorf("photo deletion: HTTP %d", status)
	}
	return nil
}
