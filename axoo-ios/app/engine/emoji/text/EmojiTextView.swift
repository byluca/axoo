import UIKit

/// Native TextKit surface; mapping, editing and animation layout have separate owners.
@MainActor
class EmojiTextView: UITextView {
    private let usesComposerMetrics: Bool
    private lazy var renderer = AnimatedEmojiRenderer(view: self)
    private lazy var editor = EmojiTextEditor(view: self)
    weak var emojiToggle: EmojiKeyboardToggle?
    var isApplyingDocument = false
    private var styleKey = ""
    var linksOnlyInteraction = false
    private let readsLinks: Bool
    var lineHeightMultiple: CGFloat = 1
    var paragraphStyle: NSParagraphStyle {
        let style = NSMutableParagraphStyle()
        style.lineHeightMultiple = lineHeightMultiple
        return style
    }
    var playbackEnabled = true {
        didSet { if oldValue != playbackEnabled { setNeedsLayout(); EmojiPlaybackCoordinator.shared.setNeedsVisibilityUpdate() } }
    }
    init(readOnly: Bool = false) {
        usesComposerMetrics = !readOnly
        readsLinks = readOnly
        linksOnlyInteraction = readOnly
        let storage = NSTextStorage()
        let manager = NSLayoutManager()
        let container = NSTextContainer(size: CGSize(width: 0, height: CGFloat.greatestFiniteMagnitude))
        container.widthTracksTextView = true
        storage.addLayoutManager(manager); manager.addTextContainer(container)
        super.init(frame: .zero, textContainer: container)
        backgroundColor = .clear
        keyboardAppearance = .light
        textContainerInset = .zero
        container.lineFragmentPadding = 0
        isScrollEnabled = false
        clipsToBounds = !readOnly
        showsVerticalScrollIndicator = false
        isEditable = !readOnly
        isSelectable = true
        if readOnly { linkTextAttributes = [.foregroundColor: AxooTheme.link, .underlineStyle: 0] }
        NotificationCenter.default.addObserver(self, selector: #selector(textChanged), name: UITextView.textDidChangeNotification, object: self)
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    var document: EmojiDocument { EmojiTextMapper.document(from: textStorage) }
    func setDocument(_ value: EmojiDocument, enlargeEmojiOnly: Bool = false) {
        guard markedTextRange == nil else { return }
        let baseFont = font ?? AxooTypography.postBody
        let count = enlargeEmojiOnly ? value.emojiOnlyCount : nil
        let bodyFont = count.map { baseFont.withSize([56, 50, 42, 34, 28][$0 - 1]) } ?? baseFont
        let edge: CGFloat = count.map { [64, 56, 48, 40, 32][$0 - 1] }
            ?? (usesComposerMetrics ? EmojiTextMetrics.composerEmojiEdge(for: baseFont) : baseFont.lineHeight * 1.4)
        let key = "\(bodyFont.fontName)-\(bodyFont.pointSize)-\(edge)-\(lineHeightMultiple)-\(textColor?.description ?? "")"
        let displayedFont = textStorage.length > 0 ? textStorage.attribute(.font, at: 0, effectiveRange: nil) as? UIFont : bodyFont
        guard document != value || styleKey != key || displayedFont != bodyFont else { return }
        isApplyingDocument = true
        let selection = selectedRange
        let rendered = NSMutableAttributedString(attributedString: EmojiTextMapper.attributed(value, font: bodyFont, color: textColor ?? AxooTheme.textPrimary, edge: edge,
                                        paragraphStyle: paragraphStyle, preservesFontLineHeight: usesComposerMetrics || count == nil))
        if readsLinks { WebLinks.apply(to: rendered) }
        attributedText = rendered
        styleKey = key
        typingAttributes = [.font: baseFont, .foregroundColor: textColor ?? AxooTheme.textPrimary, .paragraphStyle: paragraphStyle]
        let start = min(selection.location, textStorage.length)
        selectedRange = NSRange(location: start, length: min(selection.length, textStorage.length - start))
        isApplyingDocument = false
        setNeedsLayout()
    }
    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        guard super.point(inside: point, with: event) else { return false }
        guard linksOnlyInteraction else { return true }
        let location = CGPoint(x: point.x - textContainerInset.left + contentOffset.x,
                               y: point.y - textContainerInset.top + contentOffset.y)
        layoutManager.ensureLayout(for: textContainer)
        let glyph = layoutManager.glyphIndex(for: location, in: textContainer)
        guard glyph < layoutManager.numberOfGlyphs,
              layoutManager.boundingRect(forGlyphRange: NSRange(location: glyph, length: 1), in: textContainer).contains(location) else { return false }
        let character = layoutManager.characterIndexForGlyph(at: glyph)
        return character < textStorage.length && textStorage.attribute(.link, at: character, effectiveRange: nil) != nil
    }
    var onMediaRequested: (Bool) -> Void {
        get { editor.onMediaRequested }
        set { editor.onMediaRequested = newValue }
    }
    func connectPicker(to toggle: EmojiKeyboardToggle) { editor.connectPicker(to: toggle) }
    var supportsMedia: Bool {
        get { editor.supportsMedia }
        set { editor.supportsMedia = newValue }
    }
    func insertEmoji(_ emoji: Axooji) { editor.insertEmoji(emoji) }
    override func copy(_ sender: Any?) { editor.copy(sender) }
    override func cut(_ sender: Any?) { editor.cut(sender) }
    override func paste(_ sender: Any?) { editor.paste(sender) }
    override func resignFirstResponder() -> Bool {
        let result = super.resignFirstResponder()
        if result { editor.didResignFirstResponder() }
        return result
    }
    override func becomeFirstResponder() -> Bool {
        let result = super.becomeFirstResponder()
        if result { editor.didBecomeFirstResponder() }
        return result
    }
    @objc private func textChanged() { setNeedsLayout() }
    override func layoutSubviews() { super.layoutSubviews(); renderer.layout() }
}
