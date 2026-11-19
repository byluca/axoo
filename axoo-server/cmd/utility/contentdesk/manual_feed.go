package main

import (
	"github.com/google/uuid"
	"net/http"
)

func (d *desk) listManualPosts(w http.ResponseWriter, r *http.Request) {
	actor := r.URL.Query().Get("actor")
	if !d.requireActor(w, actor) {
		return
	}
	page, err := d.postService.ListPosts(actor, "new", r.URL.Query().Get("cursor"), 15, 0)
	if err != nil {
		writeInternalError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, page)
}

func (d *desk) listManualComments(w http.ResponseWriter, r *http.Request) {
	actor := r.URL.Query().Get("actor")
	if !d.requireActor(w, actor) {
		return
	}
	if _, err := uuid.Parse(r.PathValue("id")); err != nil {
		writeError(w, http.StatusBadRequest, "post non valido")
		return
	}
	rows, err := d.commentService.ListComments(r.PathValue("id"), actor)
	if err != nil {
		writeError(w, http.StatusBadRequest, "post non disponibile")
		return
	}
	writeJSON(w, http.StatusOK, rows)
}
