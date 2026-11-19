import Foundation

@MainActor
final class PersonalStickerRepository {
    private let api: APIClient
    init(api: APIClient) { self.api = api }

    func list() async throws -> [PostEditorMedia] {
        let data = try await api.request("GET", path: "media/stickers/mine")
        let payload = try APIJSON.decode(JSONValue.self, from: data)
        guard case .object(let fields) = payload, case .array(let items)? = fields["items"] else { throw HTTPFailure(status: 502) }
        return items.compactMap { PostEditorMedia.fromPayload($0) }
    }

    func save(_ sticker: PostEditorMedia) async throws -> PostEditorMedia {
        let body = try JSONEncoder().encode(sticker.payload)
        let data = try await api.request("POST", path: "media/stickers/save", body: body)
        let payload = try APIJSON.decode(JSONValue.self, from: data)
        guard let saved = PostEditorMedia.fromPayload(payload) else { throw HTTPFailure(status: 502) }
        return saved
    }

    func create(_ png: Data) async throws -> PostEditorMedia {
        let payload = try await api.uploadSticker(png)
        guard let sticker = PostEditorMedia.fromPayload(payload), sticker.kind == .sticker,
              sticker.provider == "axoo" else { throw HTTPFailure(status: 502) }
        return sticker
    }
}
