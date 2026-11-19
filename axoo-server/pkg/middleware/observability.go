package middleware

import (
	"fmt"
	"log"
	"sync/atomic"
	"time"

	"github.com/gofiber/fiber/v2"
)

var (
	httpRequests          atomic.Uint64
	httpErrors            atomic.Uint64
	httpDurationMicros    atomic.Uint64
	commentDeduplications atomic.Uint64
)

// Observe records low-cardinality API health metrics and logs only slow/error requests.
func Observe(c *fiber.Ctx) error {
	started := time.Now()
	err := c.Next()
	duration := time.Since(started)
	httpRequests.Add(1)
	httpDurationMicros.Add(uint64(duration.Microseconds()))
	status := c.Response().StatusCode()
	if err != nil || status >= 500 {
		httpErrors.Add(1)
	}
	if err != nil || status >= 500 || duration >= 750*time.Millisecond {
		log.Printf("http request_id=%s method=%s path=%s status=%d duration_ms=%d err=%v", c.GetRespHeader(fiber.HeaderXRequestID), c.Method(), c.Path(), status, duration.Milliseconds(), err)
	}
	return err
}

func RecordCommentDeduplication() { commentDeduplications.Add(1) }

func Metrics(c *fiber.Ctx) error {
	requests := httpRequests.Load()
	avgMicros := uint64(0)
	if requests > 0 {
		avgMicros = httpDurationMicros.Load() / requests
	}
	c.Set(fiber.HeaderContentType, "text/plain; version=0.0.4; charset=utf-8")
	return c.SendString(fmt.Sprintf(
		"# TYPE axoo_http_requests_total counter\naxoo_http_requests_total %d\n"+
			"# TYPE axoo_http_errors_total counter\naxoo_http_errors_total %d\n"+
			"# TYPE axoo_http_average_duration_microseconds gauge\naxoo_http_average_duration_microseconds %d\n"+
			"# TYPE axoo_comment_deduplications_total counter\naxoo_comment_deduplications_total %d\n",
		requests, httpErrors.Load(), avgMicros, commentDeduplications.Load(),
	))
}
