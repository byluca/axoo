package main

import "net/http"

func (d *desk) votePoll(w http.ResponseWriter, r *http.Request) {
	var request struct {
		ActorID  string `json:"actor_id"`
		PostID   string `json:"post_id"`
		OptionID string `json:"option_id"`
	}
	if !decodeJSON(w, r, &request) || !d.requireActor(w, request.ActorID) {
		return
	}
	poll, err := d.postService.VotePoll(request.PostID, request.ActorID, request.OptionID)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, poll)
}
