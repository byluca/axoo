package main

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"axoo-server/internal/analytics"
)

func TestStatisticsRequireDeskSession(t *testing.T) {
	d := &desk{sessionToken: "private"}
	response := httptest.NewRecorder()
	d.authorized(d.analyticsOverview)(response, httptest.NewRequest("GET", "/api/analytics", nil))
	if response.Code != http.StatusUnauthorized {
		t.Fatalf("statistics exposed without private session: %d", response.Code)
	}
}

func TestStatisticsRejectInvalidFilters(t *testing.T) {
	d := &desk{analyticsService: analytics.NewService(nil)}
	for _, query := range []string{"days=abc", "days=2", "days=-1", "university_id=invalid"} {
		response := httptest.NewRecorder()
		d.analyticsOverview(response, httptest.NewRequest("GET", "/api/analytics?"+query, nil))
		if response.Code != http.StatusBadRequest {
			t.Fatalf("%s: status %d", query, response.Code)
		}
	}
}
