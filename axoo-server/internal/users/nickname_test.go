package users

import "testing"

func TestNicknameNormalization(t *testing.T) {
	if got, err := normalizeNickname(" Student42 "); err != nil || got != "student42" {
		t.Fatalf("normal nickname: %s %v", got, err)
	}
}
