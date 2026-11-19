import Foundation

struct AccountProfile: Decodable, Equatable {
    let id: String
    let nickname: String
    let universityId: String
    let universityName: String?
    let isModerator: Bool?
    let isAdmin: Bool?
    var avatarConfig: AvatarConfiguration?
    var bio: String? = nil
    var bioEntities: [JSONValue]? = nil
    var isProfilePublic: Bool? = nil

    var bioDocument: EmojiDocument {
        EmojiDocument(text: bio ?? "", entities: EmojiEntity.decode(bioEntities))
    }
}

extension AccountProfile {
    static let bioLimit = 200

    static func bioValidationError(_ document: EmojiDocument) -> String? {
        if document.text.unicodeScalars.count > bioLimit { return "La bio può avere massimo \(bioLimit) caratteri." }
        if document.entities.count > 20 { return "Puoi inserire al massimo 20 emoji personalizzate." }
        if document.validEntities.count != document.entities.count { return "Le emoji della bio non sono valide." }
        return nil
    }

    static func nicknameValidationError(_ value: String) -> String? {
        if value.count < 3 { return "Il nickname deve avere almeno 3 caratteri" }
        if value.count > 24 { return "Il nickname può avere massimo 24 caratteri" }
        if !value.unicodeScalars.allSatisfy({ (97...122).contains($0.value) || (48...57).contains($0.value) }) {
            return "Usa solo lettere minuscole e numeri"
        }
        return nil
    }
}
