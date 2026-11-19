import Foundation

struct PostEditorMedia: Equatable {
    enum Kind: String { case photo, gif, sticker, meme }
    let kind: Kind
    let url: String
    let previewURL: String
    let width: Int
    let height: Int
    let mimeType: String
    let provider: String?
    let providerID: String?
    let photoData: Data?
    let id: String
    // Static provider preview; also used for published GIF thumbnails.
    var loadingPreviewURL: String? = nil
    var searchQuery = ""
    var previewFallbackURL: String? = nil

    static func fromPayload(_ payload: JSONValue?) -> PostEditorMedia? {
        guard case .object(let fields)? = payload,
              case .string(let url)? = fields["url"], !url.isEmpty else { return nil }
        func string(_ key: String) -> String? {
            guard case .string(let value)? = fields[key] else { return nil }
            return value
        }
        func integer(_ key: String) -> Int {
            guard case .number(let value)? = fields[key], value.isFinite, value >= 0, value <= 8192 else { return 0 }
            return Int(value)
        }
        let kind = Kind(rawValue: string("type") ?? "") ?? .photo
        return PostEditorMedia(kind: kind, url: url, previewURL: string("thumbnail_url") ?? url,
            width: integer("width"), height: integer("height"), mimeType: string("mime_type") ?? "image/jpeg",
            provider: string("provider"), providerID: string("provider_id"), photoData: nil, id: url,
            previewFallbackURL: string("preview_fallback_url"))
    }

    var payload: JSONValue {
        var fields: [String: JSONValue] = [
            "url": .string(url), "thumbnail_url": .string(kind == .gif ? loadingPreviewURL ?? previewURL : previewURL),
            "width": .number(Double(width)), "height": .number(Double(height)),
            "type": .string(kind.rawValue), "mime_type": .string(mimeType),
            "provider": provider.map(JSONValue.string) ?? .null,
            "provider_id": providerID.map(JSONValue.string) ?? .null
        ]
        if let previewFallbackURL { fields["preview_fallback_url"] = .string(previewFallbackURL) }
        return .object(fields)
    }
}

struct PostEditorDraft: Equatable {
    var body = ""
    var entities: [EmojiEntity] = []
    var category = ""
    var media: PostEditorMedia? = nil
    var mediaRemoved = false
    var pollOptions: [PollOptionInput]? = nil
    var characterCount: Int { body.unicodeScalars.count }
    var hasContent: Bool { !body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !category.isEmpty || media != nil || pollOptions != nil }
    var isValid: Bool {
        !body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && characterCount <= 500 &&
        (pollOptions.map { (2...4).contains($0.count) && $0.allSatisfy(\.isValid) } ?? true)
    }
    var normalized: Self {
        let document = EmojiDocument(text: body, entities: entities).normalized
        return Self(body: document.text, entities: document.entities, category: category, media: media, mediaRemoved: mediaRemoved,
                    pollOptions: pollOptions?.map(\.normalized))
    }
    static let categories = ["Meme", "Spotted", "Esami", "Confessioni", "Party"]
}

struct PostEditorAuthor: Equatable {
    let nickname: String
    let university: String
    let avatar: AvatarIdentity
}

enum PostEditorMode: Equatable { case create, edit(postID: String) }

struct PostEditorResult: Equatable {
    let postID: String
    let draft: PostEditorDraft
    var mediaURL: String? = nil
    var mediaPreviewURL: String? = nil
    var mediaChanged = false
    var mediaWidth = 0
    var mediaHeight = 0
    func applying(to post: Post) -> Post {
        guard post.poll == nil else { return post }
        var updated = post
        updated.body = draft.body
        updated.entities = draft.entities
        if mediaChanged {
            updated.mediaURL = mediaURL
            updated.mediaPreviewURL = mediaPreviewURL
            updated.mediaWidth = mediaWidth
            updated.mediaHeight = mediaHeight
        }
        updated.category = draft.category
        updated.isEdited = true
        return updated
    }
}

@MainActor
protocol PostEditorService {
    var author: PostEditorAuthor? { get }
    var mediaAPI: APIClient? { get }
    func mediaForEditing(_ postID: String) -> PostEditorMedia?
    func save(_ draft: PostEditorDraft, mode: PostEditorMode, clientID: String) async throws -> PostEditorResult
}

extension PostEditorService {
    var author: PostEditorAuthor? { nil }
    var mediaAPI: APIClient? { nil }
    func mediaForEditing(_ postID: String) -> PostEditorMedia? { nil }
}

enum PostEditorValidationError: LocalizedError {
    case invalidBody
    var errorDescription: String? { "Inserisci un testo valido di massimo 500 caratteri." }
}
