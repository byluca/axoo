import Foundation

/// API offsets count Unicode scalars (Android code points), never UTF-16 units.
struct EmojiEntity: Codable, Equatable {
    var type = "custom_emoji"
    var offset: Int
    var length: Int
    var id: Int
    var userID: String? = nil
    var isAnimated: Bool { type == "custom_emoji" }
    var payload: JSONValue {
        var fields: [String: JSONValue] = ["type": .string(type), "offset": .number(Double(offset)),
                 "length": .number(Double(length)), "custom_emoji_id": .number(Double(id))]
        if let userID { fields["user_id"] = .string(userID) }
        return .object(fields)
    }
    var dictionary: [String: Any] {
        var fields: [String: Any] = ["type": type, "offset": offset, "length": length, "custom_emoji_id": id]
        if let userID { fields["user_id"] = userID }
        return fields
    }
    static func decode(_ values: [JSONValue]?) -> [Self] {
        (values ?? []).compactMap { value in
            guard case .object(let fields) = value,
                  case .string(let type)? = fields["type"], ["custom_emoji", "static_emoji"].contains(type) else { return nil }
            func integer(_ key: String) -> Int? {
                guard case .number(let value)? = fields[key], value.isFinite,
                      value >= 0, value < Double(Int.max), value.rounded() == value else { return nil }
                return Int(value)
            }
            guard let offset = integer("offset"), let length = integer("length"), length > 0,
                  let id = integer("custom_emoji_id") else { return nil }
            let userID: String?
            if case .string(let value)? = fields["user_id"] { userID = value } else { userID = nil }
            return Self(type: type, offset: offset, length: length, id: id, userID: userID)
        }
    }
}

