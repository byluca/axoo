package reports

import (
	"axoo-server/internal/models"
	"bytes"
	"encoding/json"
	"log"
	"net/http"
	"os"
	"time"
)

func notifyReport(report *models.Report) {
	log.Printf("moderation report_id=%s target_type=%s target_id=%s", report.ID, report.TargetType, report.TargetID)
	url := os.Getenv("MODERATION_ALERT_WEBHOOK")
	if url == "" {
		return
	}
	body, err := json.Marshal(map[string]any{"event": "new_report", "report_id": report.ID, "target_type": report.TargetType, "target_id": report.TargetID, "reason": report.Reason})
	if err != nil {
		return
	}
	request, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		log.Print("moderation alert: invalid webhook")
		return
	}
	request.Header.Set("Content-Type", "application/json")
	response, err := (&http.Client{Timeout: 5 * time.Second}).Do(request)
	if err != nil {
		log.Print("moderation alert: webhook unavailable; check the queue")
		return
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		log.Printf("moderation alert: status=%d", response.StatusCode)
	}
}
