package users

import (
	"axoo-server/internal/models"
	"errors"
	"testing"
)

func TestClientCannotAssignOfficialIdentity(t *testing.T) {
	service := NewService(nil)
	for _, kind := range []any{"aup", "axoo", "forged", nil} {
		if err := service.UpdateAvatarConfig("user", models.AvatarConfig{"official_account": kind}); !errors.Is(err, ErrOfficialAvatar) {
			t.Fatalf("reserved avatar metadata accepted: %v", err)
		}
	}
	for _, name := range []string{"axoo", "aup", " AUP "} {
		available, err := service.NicknameAvailable("user", name)
		if err != nil || available {
			t.Fatal("official nickname must remain reserved")
		}
		if _, err := service.UpdateNickname("user", name); !errors.Is(err, ErrNicknameUnavailable) {
			t.Fatal("official nickname must not be reassigned")
		}
	}
}
