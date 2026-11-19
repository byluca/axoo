package storage

import (
	"axoo-server/internal/media"
	"context"
	"encoding/json"
	"fmt"
	"strings"
)

func (s *SupabaseStorage) PutSticker(ctx context.Context, path string, data []byte) (string, error) {
	status, err := s.photoRequest(ctx, "POST", "object/"+media.StickerBucket+"/"+path, "image/png", data)
	if err != nil {
		return "", err
	}
	if status < 200 || status >= 300 {
		return "", fmt.Errorf("sticker upload: HTTP %d", status)
	}
	return strings.TrimRight(s.config.URL, "/") + "/storage/v1/object/public/" + media.StickerBucket + "/" + path, nil
}

func (s *SupabaseStorage) CopySticker(ctx context.Context, source, destination string) error {
	body, _ := json.Marshal(map[string]string{"bucketId": media.StickerBucket, "sourceKey": source, "destinationKey": destination})
	status, err := s.photoRequest(ctx, "POST", "object/copy", "application/json", body)
	if err != nil {
		return err
	}
	if status < 200 || status >= 300 {
		return fmt.Errorf("sticker copy: HTTP %d", status)
	}
	return nil
}
