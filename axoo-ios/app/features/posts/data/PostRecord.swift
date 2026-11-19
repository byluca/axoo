import Foundation

/// Original server values are retained separately from card presentation.
struct PostRecord: Decodable {
    let id: String
    let userId: String
    let nickname: String?
    let universityId: String?
    let universityName: String?
    var body: String?
    var flair: String?
    let upvotes: Int?
    let downvotes: Int?
    let commentsCount: Int?
    let myVote: String?
    let createdAt: String
    var updatedAt: String?
    let media: JSONValue?
    let entities: [JSONValue]?
    let avatarConfig: AvatarConfiguration?
    var poll: PostPoll? = nil
}

/// Opaque payloads survive text-only edits without losing media metadata.
indirect enum JSONValue: Codable, Equatable {
    case string(String), number(Double), bool(Bool), object([String: JSONValue]), array([JSONValue]), null
    init(from decoder: Decoder) throws {
        let value = try decoder.singleValueContainer()
        if value.decodeNil() { self = .null }
        else if let item = try? value.decode(Bool.self) { self = .bool(item) }
        else if let item = try? value.decode(String.self) { self = .string(item) }
        else if let item = try? value.decode(Double.self) { self = .number(item) }
        else if let item = try? value.decode([String: JSONValue].self) { self = .object(item) }
        else { self = .array(try value.decode([JSONValue].self)) }
    }
    func encode(to encoder: Encoder) throws {
        var value = encoder.singleValueContainer()
        switch self {
        case .null: try value.encodeNil()
        case .string(let item): try value.encode(item)
        case .number(let item): try value.encode(item)
        case .bool(let item): try value.encode(item)
        case .object(let item): try value.encode(item)
        case .array(let item): try value.encode(item)
        }
    }
}
