import UIKit

/// Resolves passive spans after TextKit layout and positions shared animation hosts.
@MainActor
final class AnimatedEmojiRenderer {
    private weak var view: EmojiTextView?
    private var hosts: [Int: AnimatedLottieView] = [:]
    private let playbackGroup = UUID()
    init(view: EmojiTextView) { self.view = view }
    func layout() {
        guard let view else { return }
        view.layoutManager.ensureLayout(for: view.textContainer)
        var retained = Set<Int>()
        view.textStorage.enumerateAttribute(.attachment, in: NSRange(location: 0, length: view.textStorage.length)) { value, range, _ in
            guard let attachment = value as? AnimatedEmojiSpan,
                  let emoji = AxoojiCatalog.shared.byID[attachment.entity.id] else { return }
            let glyph = view.layoutManager.glyphIndexForCharacter(at: range.location)
            guard glyph < view.layoutManager.numberOfGlyphs, NSLocationInRange(glyph, view.layoutManager.glyphRange(for: view.textContainer)) else { return }
            let truncated = view.layoutManager.truncatedGlyphRange(inLineFragmentForGlyphAt: glyph)
            guard truncated.location == NSNotFound || !NSLocationInRange(glyph, truncated) else { return }
            let line = view.layoutManager.lineFragmentRect(forGlyphAt: glyph, effectiveRange: nil)
            let position = view.layoutManager.location(forGlyphAt: glyph)
            let emojiY = attachment.preservesFontLineHeight
                ? line.midY - attachment.renderEdge / 2
                : line.minY + position.y - attachment.bounds.maxY
            let rect = CGRect(x: view.textContainerInset.left + line.minX + position.x,
                              y: view.textContainerInset.top + emojiY,
                              width: attachment.renderEdge, height: attachment.renderEdge)
            guard rect.intersects(view.bounds) else { return }
            retained.insert(range.location)
            let host = hosts[range.location] ?? AnimatedLottieView(frame: rect)
            host.showsUnicodeFallback = false
            host.textPlaybackGroup = playbackGroup
            if hosts[range.location] == nil { hosts[range.location] = host; view.addSubview(host) }
            host.frame = rect
            host.configure(emoji)
            host.animates = attachment.entity.isAnimated
            host.playbackEnabled = view.playbackEnabled && attachment.entity.isAnimated
        }
        for key in Array(hosts.keys) where !retained.contains(key) { hosts.removeValue(forKey: key)?.removeFromSuperview() }
    }
    func release() {
        hosts.values.forEach { $0.removeFromSuperview() }
        hosts.removeAll()
    }
}
