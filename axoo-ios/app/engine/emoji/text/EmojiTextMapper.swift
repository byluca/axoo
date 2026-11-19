import UIKit

@MainActor
enum EmojiTextMapper {
    static func document(from value: NSAttributedString) -> EmojiDocument {
        var text = ""
        var entities: [EmojiEntity] = []
        value.enumerateAttribute(.attachment, in: NSRange(location: 0, length: value.length)) { attachment, range, _ in
            if let attachment = attachment as? AnimatedEmojiSpan {
                var entity = attachment.entity
                entity.offset = text.unicodeScalars.count
                entity.length = attachment.fallback.unicodeScalars.count
                text += attachment.fallback
                entities.append(entity)
            } else { text += (value.string as NSString).substring(with: range) }
        }
        return EmojiDocument(text: text, entities: entities)
    }
    static func attributed(_ document: EmojiDocument, font: UIFont, color: UIColor, edge: CGFloat,
                           paragraphStyle: NSParagraphStyle = .default,
                           preservesFontLineHeight: Bool = false) -> NSAttributedString {
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color, .paragraphStyle: paragraphStyle]
        let result = NSMutableAttributedString(string: document.text, attributes: attributes)
        for entity in document.validEntities.reversed() {
            let range = document.range(of: entity)
            let source = (document.text as NSString).substring(with: range)
            let attachment = AnimatedEmojiSpan(entity: entity, fallback: source, font: font, edge: edge,
                                                 preservesFontLineHeight: preservesFontLineHeight)
            result.replaceCharacters(in: range, with: NSAttributedString(attachment: attachment))
        }
        result.addAttributes(attributes, range: NSRange(location: 0, length: result.length))
        return result
    }
}
