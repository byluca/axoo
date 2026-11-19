package main

import (
	"maps"
	"net/http"
	"net/http/httptest"
	"slices"
	"testing"

	"github.com/google/uuid"
)

func TestAuthorizedRequiresSessionCookie(t *testing.T) {
	d := &desk{sessionToken: "secret"}
	handler := d.authorized(func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(http.StatusNoContent) })

	withoutCookie := httptest.NewRecorder()
	handler(withoutCookie, httptest.NewRequest(http.MethodGet, "/api/bootstrap", nil))
	if withoutCookie.Code != http.StatusUnauthorized {
		t.Fatalf("without cookie status = %d, want %d", withoutCookie.Code, http.StatusUnauthorized)
	}

	withCookie := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodGet, "/api/bootstrap", nil)
	request.AddCookie(&http.Cookie{Name: sessionCookie, Value: "secret"})
	handler(withCookie, request)
	if withCookie.Code != http.StatusNoContent {
		t.Fatalf("with cookie status = %d, want %d", withCookie.Code, http.StatusNoContent)
	}
}

func TestOpenExchangesTokenForHttpOnlyCookie(t *testing.T) {
	d := &desk{sessionToken: "secret"}
	recorder := httptest.NewRecorder()
	d.open(recorder, httptest.NewRequest(http.MethodGet, "/open?token=secret", nil))

	if recorder.Code != http.StatusSeeOther {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusSeeOther)
	}
	cookies := recorder.Result().Cookies()
	if len(cookies) != 1 || cookies[0].Name != sessionCookie || !cookies[0].HttpOnly {
		t.Fatalf("unexpected cookies: %#v", cookies)
	}
}

func TestOpenRejectsWrongToken(t *testing.T) {
	d := &desk{sessionToken: "secret"}
	recorder := httptest.NewRecorder()
	d.open(recorder, httptest.NewRequest(http.MethodGet, "/open?token=wrong", nil))
	if recorder.Code != http.StatusForbidden {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusForbidden)
	}
}

func TestNicknamePattern(t *testing.T) {
	for _, valid := range []string{"martybari", "fuorisede24", "abc"} {
		if !nicknamePattern.MatchString(valid) {
			t.Errorf("expected %q to be valid", valid)
		}
	}
	for _, invalid := range []string{"ab", "nome con spazi", "accentà", "thisnicknameisdefinitelytoolong", "marty_bari", "Fuorisede24", "nome-utente"} {
		if nicknamePattern.MatchString(invalid) {
			t.Errorf("expected %q to be invalid", invalid)
		}
	}
}

func TestGeneratedAvatarUsesAppFieldNames(t *testing.T) {
	avatar := generatedAvatar(uuid.MustParse("12345678-1234-4234-8234-123456789abc"))
	for _, key := range []string{"backgroundColor", "hairVariant", "hairColor", "eyesVariant", "eyebrowsVariant", "mouthVariant", "skinColor"} {
		if avatar[key] == nil || avatar[key] == "" {
			t.Errorf("missing avatar field %q in %#v", key, avatar)
		}
	}
}

func TestGeneratedAvatarUsesOnlyAxooMobilePresets(t *testing.T) {
	avatar := generatedAvatar(uuid.MustParse("12345678-1234-4234-8234-123456789abc"))
	allowed := map[string][]string{
		"backgroundColor": axooAvatarBackgrounds,
		"hairVariant":     axooAvatarHair,
		"hairColor":       axooAvatarHairColors,
		"eyesVariant":     axooAvatarEyes,
		"eyebrowsVariant": axooAvatarEyebrows,
		"mouthVariant":    axooAvatarMouths,
		"skinColor":       axooAvatarSkinColors,
		"glassesVariant":  axooAvatarGlasses,
		"earringsVariant": axooAvatarEarrings,
		"detailsVariant":  axooAvatarDetails,
	}
	for key, value := range avatar {
		stringValue, ok := value.(string)
		if !ok || !slices.Contains(allowed[key], stringValue) {
			t.Fatalf("%s=%#v is not an Axoo mobile preset", key, value)
		}
	}
	if avatar["skinColor"] != axooAvatarSkinColors[0] {
		t.Fatalf("default skinColor = %q, want %q", avatar["skinColor"], axooAvatarSkinColors[0])
	}
}

func TestGeneratedAvatarIsDeterministicForTheSameProfile(t *testing.T) {
	seed := uuid.MustParse("12345678-1234-4234-8234-123456789abc")
	if first, second := generatedAvatar(seed), generatedAvatar(seed); !maps.Equal(first, second) {
		t.Fatalf("avatar changed for the same seed: %#v != %#v", first, second)
	}
}

func TestContentDeskProfileIsCreatedAsAlphaUser(t *testing.T) {
	user := newContentDeskUser(
		"12345678-1234-4234-8234-123456789abc",
		"espresso",
		"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
		nil,
	)
	if !user.IsAlphaTester {
		t.Fatal("content desk user must be marked as alpha")
	}
	if user.EmailHash != "contentdesk-12345678-1234-4234-8234-123456789abc" {
		t.Fatalf("unexpected synthetic email hash: %q", user.EmailHash)
	}
}
