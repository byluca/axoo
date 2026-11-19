import UIKit

@MainActor
enum EmojiTextMetrics {
    static func composerEmojiEdge(for font: UIFont) -> CGFloat {
        max(1, abs(font.ascender) + abs(font.descender)) * 1.4
    }
}
