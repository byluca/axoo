package main

import (
	"strings"
	"testing"
)

func TestManagedNicknamesHaveDifferentNamesAndStyles(t *testing.T) {
	existing := []string{"LANTERNA", "OGGINONSTUDIO", "ALTF4"}
	seen := map[string]bool{}
	for _, name := range existing {
		seen[strings.ToLower(name)] = true
	}
	names, err := generateManagedNicknames(300, existing)
	if err != nil {
		t.Fatal(err)
	}
	var short, long int
	stems := map[string]bool{}
	for _, name := range names {
		key := strings.ToLower(name)
		stem := strings.TrimRight(key, "0123456789")
		if seen[key] || stems[stem] || !nicknamePattern.MatchString(name) {
			t.Fatalf("repeated or invalid nickname %q", name)
		}
		seen[key], stems[stem] = true, true
		if len(name) <= 10 {
			short++
		} else {
			long++
		}
	}
	if short < 60 || long < 60 {
		t.Fatalf("not enough variety: %d short, %d long", short, long)
	}
	for name := range seen {
		existing = append(existing, name)
	}
	more, err := generateManagedNicknames(300, existing)
	if err != nil {
		t.Fatal(err)
	}
	for _, name := range more {
		if seen[strings.ToLower(name)] {
			t.Fatalf("nickname reused across batches: %s", name)
		}
	}
}
