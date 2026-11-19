import Foundation

/// Swift Character keeps joined emoji and flags together when counting up to five.
enum EmojiOnlyClassifier {
    static func count(in document: EmojiDocument) -> Int? {
        let remaining = NSMutableString(string: document.text)
        let custom = document.validEntities
        for entity in custom.reversed() { remaining.replaceCharacters(in: document.range(of: entity), with: "") }
        var count = custom.count
        for character in remaining as String {
            if character.unicodeScalars.allSatisfy({ CharacterSet.whitespacesAndNewlines.contains($0) }) { continue }
            let scalars = character.unicodeScalars
            guard scalars.contains(where: { $0.properties.isEmojiPresentation || $0.value == 0xFE0F || $0.value == 0x20E3 }) else { return nil }
            count += 1
        }
        return (1...5).contains(count) ? count : nil
    }
}
