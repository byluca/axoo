package main

import (
	"errors"
	"net/http"
	"strconv"

	"axoo-server/internal/analytics"
	"github.com/google/uuid"
)

func (d *desk) analyticsOverview(w http.ResponseWriter, r *http.Request) {
	days := 7
	if raw := r.URL.Query().Get("days"); raw != "" {
		value, err := strconv.Atoi(raw)
		if err != nil {
			writeError(w, http.StatusBadRequest, "intervallo non valido")
			return
		}
		days = value
	}
	universityID := r.URL.Query().Get("university_id")
	if universityID != "" {
		if _, err := uuid.Parse(universityID); err != nil {
			writeError(w, http.StatusBadRequest, "campus non valido")
			return
		}
	}
	result, err := d.analyticsService.Overview(days, universityID)
	if err != nil {
		if errors.Is(err, analytics.ErrInvalidRange) {
			writeError(w, http.StatusBadRequest, err.Error())
			return
		}
		writeInternalError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, result)
}
