import Foundation

struct RemoteMediaPage {
    let items: [PostEditorMedia]
    let nextOffset: Int?
}

/// Fetches media metadata; thumbnail downloads belong to the cells.
@MainActor
final class RemoteMediaRepository {
    private let klipy: APIClient
    init(klipy: APIClient = KlipyClient.shared) { self.klipy = klipy }

    func rememberSelection(_ media: PostEditorMedia) { (klipy as? KlipyClient)?.rememberSelection(media) }

    func load(kind: PostEditorMedia.Kind, query: String, offset: Int) async throws -> RemoteMediaPage {
        let collection = kind == .meme ? "static-memes" : (kind == .sticker ? "stickers" : "gifs")
        var parameters = [URLQueryItem(name: "page", value: String(max(1, offset))),
            URLQueryItem(name: "per_page", value: "30"),
            URLQueryItem(name: "locale", value: Locale.current.regionCode?.lowercased() ?? "it"),
            URLQueryItem(name: "content_filter", value: "high")]
        if kind != .meme { parameters.append(URLQueryItem(name: "format_filter", value: kind == .gif ? "gif,jpg" : "gif")) }
        if !query.isEmpty { parameters.append(URLQueryItem(name: "q", value: query)) }
        let data = try await klipy.request("GET", path: "\(collection)/\(query.isEmpty ? "trending" : "search")", query: parameters, body: nil)
        try Task.checkCancellation()
        return try APIJSON.decode(KlipyResponse.self, from: data).mediaPage(kind: kind, query: query)
    }
}
