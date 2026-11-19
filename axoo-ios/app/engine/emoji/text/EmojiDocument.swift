import Foundation

struct EmojiDocument: Codable, Equatable {
    var text: String
    var entities: [EmojiEntity] = []

    var validEntities: [EmojiEntity] {
        let count = text.unicodeScalars.count
        var end = 0
        return entities.sorted { $0.offset < $1.offset }.filter { entity in
            guard entity.id >= 0, entity.offset >= end, entity.length > 0, entity.offset <= count,
                  entity.length <= count - entity.offset,
                  ["custom_emoji", "static_emoji"].contains(entity.type) else { return false }
            end = entity.offset + entity.length
            return true
        }
    }
    func range(of entity: EmojiEntity) -> NSRange {
        EmojiTextBoundary.range(of: entity, in: text)
    }
    var normalized: Self {
        let scalars = Array(text.unicodeScalars)
        let whitespace = CharacterSet.whitespacesAndNewlines
        let start = scalars.firstIndex { !whitespace.contains($0) } ?? scalars.count
        let end = scalars.lastIndex { !whitespace.contains($0) }.map { $0 + 1 } ?? start
        let trimmed = String(String.UnicodeScalarView(scalars[start..<end]))
        let adjusted = validEntities.compactMap { entity -> EmojiEntity? in
            guard entity.offset >= start, entity.offset + entity.length <= end else { return nil }
            var result = entity; result.offset -= start; return result
        }
        return Self(text: trimmed, entities: adjusted)
    }
    static func canEdit(text: String, raw: [JSONValue]?) -> Bool {
        guard (raw ?? []).allSatisfy({ value in
            guard case .object(let fields) = value else { return false }
            return Set(fields.keys).isSubset(of: ["type", "offset", "length", "custom_emoji_id", "user_id"])
        }) else { return false }
        let entities = EmojiEntity.decode(raw)
        return entities.count == (raw?.count ?? 0) && Self(text: text, entities: entities).validEntities.count == entities.count
    }
    var emojiOnlyCount: Int? { EmojiOnlyClassifier.count(in: self) }
}
