package storage

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestListMemesUsesManifestAndPaginates(t *testing.T) {
	requests := 0
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		requests++
		writer.Header().Set("Content-Type", "application/json")
		_, _ = writer.Write([]byte(`{"items":[
			{"path":"italia/meme uno.jpg","category":"italia","width":800,"height":600,"grid_path":"_grid/italia/meme-abc.webp","preview_path":"_previews/italia/meme-abc.webp"},
			{"path":"italia/meme-due.jpg","category":"italia","width":400,"height":700,"grid_path":"_grid/italia/meme-due-def.webp","preview_path":"_previews/italia/meme-due-def.webp"}
		]}`))
	}))
	defer server.Close()

	storage := NewSupabaseStorage(Config{URL: server.URL})
	first, err := storage.ListMemes("italia", 0, 1)
	if err != nil {
		t.Fatal(err)
	}
	if len(first.Items) != 1 || first.NextOffset == nil || *first.NextOffset != 1 {
		t.Fatalf("prima pagina inattesa: %+v", first)
	}
	if first.Items[0].Width != 800 || first.Items[0].Height != 600 {
		t.Fatalf("dimensioni inattese: %+v", first.Items[0])
	}
	if first.Items[0].URL != server.URL+"/storage/v1/object/public/memes/italia/meme%20uno.jpg" {
		t.Fatalf("URL non codificato: %s", first.Items[0].URL)
	}

	second, err := storage.ListMemes("italia", 1, 1)
	if err != nil {
		t.Fatal(err)
	}
	if len(second.Items) != 1 || second.NextOffset != nil {
		t.Fatalf("seconda pagina inattesa: %+v", second)
	}
	if requests != 1 {
		t.Fatalf("il manifest doveva essere richiesto una volta, richieste=%d", requests)
	}
}
