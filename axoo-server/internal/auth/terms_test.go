package auth

import (
	"errors"
	"testing"
)

func TestTermsRejectWrongVersionAndMissingAdultConfirmationBeforeDB(t *testing.T) {
	repository := NewRepository(nil)
	for _, input := range []struct {
		version string
		adult   bool
	}{{"old", true}, {TermsVersion, false}, {"", false}} {
		if err := repository.AcceptTerms("user", input.version, input.adult); !errors.Is(err, ErrTerms) {
			t.Errorf("accepted %+v", input)
		}
	}
}
