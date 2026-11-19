package gifs

// Service gestisce la logica di business per le GIF.
type Service struct {
	client GiphyClient
}

func NewService(client GiphyClient) *Service {
	return &Service{client: client}
}

func (s *Service) Trending(limit, offset int) (map[string]any, error) {
	return s.client.Trending(limit, offset)
}

func (s *Service) Search(query string, limit, offset int) (map[string]any, error) {
	return s.client.Search(query, limit, offset)
}

func (s *Service) StickersTrending(limit, offset int) (map[string]any, error) {
	return s.client.StickersTrending(limit, offset)
}
func (s *Service) StickersSearch(query string, limit, offset int) (map[string]any, error) {
	return s.client.StickersSearch(query, limit, offset)
}
