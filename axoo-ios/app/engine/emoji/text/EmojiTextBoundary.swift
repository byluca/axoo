import Foundation

/// Native selections use UTF-16; API entities use Unicode scalars.
enum EmojiTextBoundary {
    static func range(of entity: EmojiEntity, in text: String) -> NSRange {
        let scalars = text.unicodeScalars
        let start = scalars.index(scalars.startIndex, offsetBy: entity.offset)
        let end = scalars.index(start, offsetBy: entity.length)
        return NSRange(start..<end, in: text)
    }
}
