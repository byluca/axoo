import UIKit

/// Passive TextKit placeholder with entity metadata and native line metrics.
@MainActor
final class AnimatedEmojiSpan: NSTextAttachment {
    private static let transparentImage = UIGraphicsImageRenderer(size: CGSize(width: 1, height: 1)).image { _ in }
    let entity: EmojiEntity
    let fallback: String
    let renderEdge: CGFloat
    let preservesFontLineHeight: Bool
    init(entity: EmojiEntity, fallback: String, font: UIFont, edge: CGFloat, preservesFontLineHeight: Bool = false) {
        self.entity = entity; self.fallback = fallback
        self.renderEdge = edge
        self.preservesFontLineHeight = preservesFontLineHeight
        super.init(data: nil, ofType: nil)
        // Match Android's span: reserve the font's ascent/descent, not the
        // enlarged animation's height. Its full width still participates in wrapping.
        let height = preservesFontLineHeight ? max(1, abs(font.ascender) + abs(font.descender)) : edge
        bounds = CGRect(x: 0, y: font.descender, width: edge, height: height)
        // Reserve the emoji space until the animation has a frame.
        image = Self.transparentImage
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic text only") }
}

