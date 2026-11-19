import UIKit

/// Native text input with a shared optional emoji keyboard.
final class CommentComposerView: AxooDrawingView, UITextViewDelegate {
    static let keyboardGap: CGFloat = 12
    let textView = EmojiEditTextView()
    private let emojiToggle = EmojiKeyboardToggle()
    var onChange: (EmojiDocument, NSRange) -> Void = { _, _ in }
    var onSend: () -> Void = {}
    var onMediaRequested: (Bool) -> Void = { _ in }
    var onCancelReply: () -> Void = {}
    var onHeightChange: () -> Void = {}
    var onActivate: () -> Void = {}
    private let placeholder = UILabel.axoo("Commenta…", color: .secondaryLabel)
    private let replyIcon = AxooIconView(icon: .reply)
    private let preview = UILabel.axoo(color: AxooTheme.accent)
    private let excerpt = EmojiTextView(readOnly: true)
    private let counter = UILabel.axoo(color: .secondaryLabel)
    private lazy var close = UIButton.icon(.close, size: 18) { [weak self] in self?.onCancelReply() }
    private lazy var send = UIButton.icon(.send, size: 25) { [weak self] in
        guard let self = self else { return }
        self.textView.unmarkText()
        self.onChange(self.textView.document, self.textView.selectedRange)
        self.onSend()
    }
    private var showsReply = false
    private var inputRect = CGRect.zero
    private var replyRect = CGRect.zero

    init() {
        super.init(frame: .zero)
        [replyIcon, preview, excerpt, close, textView, emojiToggle, placeholder, send, counter].forEach { addSubview($0) }
        textView.backgroundColor = .clear
        textView.textColor = AxooTheme.textPrimary
        textView.tintColor = AxooTheme.accent
        replyIcon.tintColor = AxooTheme.accent
        replyIcon.accessibilityIdentifier = "reply-icon"
        close.tintColor = AxooTheme.textSecondary
        textView.font = AxooTypography.postBody
        textView.lineHeightMultiple = 1.12
        textView.textContainerInset = UIEdgeInsets(top: 10, left: 8, bottom: 10, right: 8)
        textView.textContainer.lineFragmentPadding = 0
        textView.delegate = self
        textView.connectPicker(to: emojiToggle)
        textView.onMediaRequested = { [weak self] in self?.onMediaRequested($0) }
        textView.accessibilityIdentifier = "comment-input"
        textView.returnKeyType = .default
        placeholder.isUserInteractionEnabled = false
        preview.numberOfLines = 1
        excerpt.textColor = AxooTheme.textSecondary
        excerpt.textContainer.maximumNumberOfLines = 1
        excerpt.textContainer.lineBreakMode = .byTruncatingTail
        excerpt.accessibilityIdentifier = "reply-excerpt"
        preview.lineBreakMode = .byTruncatingTail
        preview.accessibilityIdentifier = "reply-preview"
        close.accessibilityIdentifier = "cancel-reply"
        send.accessibilityIdentifier = "comment-send"
        send.setImage(send.image(for: .normal), for: .disabled)
        counter.textAlignment = .right
        counter.accessibilityIdentifier = "comment-limit"
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    func render(_ state: PostDetailState) {
        let editing = state.editingID != nil
        let document = EmojiDocument(text: state.draft.text, entities: state.draft.entities)
        if textView.document != document, textView.markedTextRange == nil {
            textView.setDocument(document)
            let start = min(state.draft.selection.location, textView.textStorage.length)
            textView.selectedRange = NSRange(location: start, length: min(state.draft.selection.length, textView.textStorage.length - start))
        }
        textView.isEditable = !state.isSaving && !state.isPostDeleted && !state.isPerformingAction
        emojiToggle.isEnabled = textView.isEditable
        placeholder.font = AxooTypography.postBody
        placeholder.text = editing ? "Modifica il commento…" : "Commenta…"
        placeholder.isHidden = !state.draft.text.isEmpty
        let target = state.items.first { $0.comment.id == state.draft.replyID }?.comment
        showsReply = state.draft.replyID != nil && !editing
        preview.text = "Rispondi a \(target?.authorLabel ?? "commento")"
        var body = target?.body ?? ""
        if target?.isDeleted == true { body = "Commento non disponibile" }
        else if body.isEmpty, let media = target?.media { body = media.kind == .sticker ? "Sticker" : "GIF" }
        preview.font = AxooTypography.replyTitle
        excerpt.font = AxooTypography.replyBody
        // Keep scalar offsets intact when flattening line breaks, then trim with entities.
        let singleLine = body.unicodeScalars.map {
            CharacterSet.whitespacesAndNewlines.contains($0) ? " " : String($0)
        }.joined()
        let entities = target?.isDeleted == true ? [] : target?.entities ?? []
        excerpt.setDocument(showsReply ? EmojiDocument(text: singleLine, entities: entities).normalized : EmojiDocument(text: ""))
        excerpt.playbackEnabled = showsReply
        [replyIcon, preview, excerpt, close].forEach { $0.isHidden = !showsReply }
        close.isEnabled = !state.isSaving && !state.isPerformingAction
        send.isEnabled = state.canSend
        send.tintColor = .white
        let count = state.draft.text.unicodeScalars.count
        counter.font = AxooTypography.postMetadata
        counter.text = state.isSaving ? "Salvataggio…" : "\(count)/\(CommentInput.limit)"
        counter.textColor = count > CommentInput.limit ? .systemRed : .secondaryLabel
        setNeedsLayout()
        setNeedsDisplay()
        onHeightChange()
    }
    func focus(selection: NSRange? = nil) {
        onActivate()
        textView.becomeFirstResponder()
        if let selection {
            let start = min(selection.location, textView.textStorage.length)
            textView.selectedRange = NSRange(location: start, length: min(selection.length, textView.textStorage.length - start))
        }
    }
    func textViewDidBeginEditing(_ textView: UITextView) { onActivate() }
    func textViewDidChange(_ textView: UITextView) {
        guard !self.textView.isApplyingDocument else { return }
        onChange(self.textView.document, textView.selectedRange)
        onHeightChange()
    }
    func textViewDidChangeSelection(_ textView: UITextView) {
        guard textView.isFirstResponder, !self.textView.isApplyingDocument else { return }
        onChange(self.textView.document, textView.selectedRange)
    }
    override func sizeThatFits(_ size: CGSize) -> CGSize { CGSize(width: size.width, height: arrange(size.width, apply: false)) }
    override func layoutSubviews() { super.layoutSubviews(); _ = arrange(bounds.width, apply: true) }
    private func arrange(_ width: CGFloat, apply: Bool) -> CGFloat {
        let inset: CGFloat = 8
        var y: CGFloat = 5
        if showsReply {
            let titleHeight = ceil(preview.font.lineHeight)
            let textWidth = max(1, width - inset * 2 - 40 - 48)
            let bodyHeight = ceil(excerpt.sizeThatFits(CGSize(width: textWidth, height: .greatestFiniteMagnitude)).height)
            // Android reserves 52pt including the 4pt gap below the reply pill.
            let height = max(52, titleHeight + bodyHeight + 12) - 4
            if apply {
                replyRect = CGRect(x: inset, y: y, width: width - inset * 2, height: height)
                let textY = y + (height - titleHeight - bodyHeight) / 2
                replyIcon.frame = CGRect(x: inset + 12, y: y + (height - 20) / 2, width: 20, height: 20)
                preview.frame = CGRect(x: inset + 40, y: textY, width: textWidth, height: titleHeight)
                excerpt.frame = CGRect(x: preview.frame.minX, y: textY + titleHeight, width: preview.frame.width, height: bodyHeight)
                close.frame = CGRect(x: width - inset - 48, y: y + (height - 48) / 2, width: 48, height: 48)
            }
            y += height + 4
        }
        
        let buttonWidth: CGFloat = 44
        let spacing: CGFloat = 8
        let pillWidth = width - inset * 2 - buttonWidth - spacing
        let textWidth = max(1, pillWidth - 56)
        
        let natural = textView.sizeThatFits(CGSize(width: textWidth, height: .greatestFiniteMagnitude)).height
        let cap = max(120, AxooTypography.postBody.lineHeight * 3 + 20)
        let inputHeight = min(cap, max(40, ceil(natural)))
        
        if apply {
            inputRect = CGRect(x: inset, y: y, width: pillWidth, height: inputHeight + 4)
            emojiToggle.frame = CGRect(x: inset + 4, y: y + inputHeight - 40, width: 40, height: 44)
            textView.frame = CGRect(x: inset + 48, y: y + 2, width: textWidth, height: inputHeight)
            textView.isScrollEnabled = natural > cap
            let textOrigin = textView.frame.origin
            placeholder.frame = CGRect(x: textOrigin.x + textView.textContainerInset.left,
                y: textOrigin.y + textView.textContainerInset.top,
                width: max(1, textView.bounds.width - textView.textContainerInset.left - textView.textContainerInset.right),
                height: placeholder.font.lineHeight)
            // Align bottom of send button with bottom of inputRect
            send.frame = CGRect(x: width - inset - buttonWidth, y: y + inputHeight + 4 - buttonWidth, width: buttonWidth, height: buttonWidth)
            counter.isHidden = true
        }
        y += inputHeight + 4
        return ceil(y + Self.keyboardGap)
    }
    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        func fill(_ path: UIBezierPath, color: UIColor) {
            context.saveGState()
            context.setShadow(offset: AxooTheme.controlShadowOffset, blur: AxooTheme.controlShadowRadius,
                              color: UIColor.black.withAlphaComponent(CGFloat(AxooTheme.controlShadowOpacity)).cgColor)
            color.setFill()
            path.fill()
            context.restoreGState()
        }
        if showsReply {
            let replyPill = UIBezierPath(roundedRect: replyRect, cornerRadius: replyRect.height / 2)
            fill(replyPill, color: AxooTheme.cardBackground)
            AxooTheme.cardBorder.setStroke()
            replyPill.lineWidth = AxooTheme.controlBorderWidth
            replyPill.stroke()
        }
        let pill = UIBezierPath(roundedRect: inputRect, cornerRadius: AxooTheme.controlCornerRadius)
        fill(pill, color: AxooTheme.cardBackground)
        AxooTheme.cardBorder.setStroke()
        pill.lineWidth = AxooTheme.controlBorderWidth
        pill.stroke()
        let sendPill = UIBezierPath(ovalIn: send.frame)
        fill(sendPill, color: send.isEnabled ? AxooTheme.accent : AxooTheme.accent.withAlphaComponent(0.4))
        AxooTheme.cardBorder.setStroke()
        sendPill.lineWidth = AxooTheme.controlBorderWidth
        sendPill.stroke()
    }
}
