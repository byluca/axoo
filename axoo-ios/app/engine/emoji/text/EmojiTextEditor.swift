import UIKit

/// Editor operations use native UTF-16 selections; the mapper restores API scalar offsets.
@MainActor
final class EmojiTextEditor {
    private weak var view: EmojiTextView?
    var onMediaRequested: (Bool) -> Void = { _ in }
    var supportsMedia = true {
        didSet { picker?.supportsMedia = supportsMedia }
    }
    private var picker: EmojiPanelView?
    private var pickerKeyboard: AxoojiKeyboardView?
    private static let pasteboardType = "com.axoo.emoji-document"
    init(view: EmojiTextView) { self.view = view }
    func connectPicker(to toggle: EmojiKeyboardToggle) {
        guard let view else { return }
        view.emojiToggle = toggle
        toggle.onToggle = { [weak self] in self?.togglePicker() }
    }
    private func togglePicker() {
        guard let view else { return }
        guard view.isEditable else { return }
        view.unmarkText()
        if view.inputView != nil { view.inputView = nil; picker?.isActive = false }
        else {
            if picker == nil {
                let view = EmojiPanelView()
                view.supportsMedia = supportsMedia
                view.onSelect = { [weak self] in self?.insertEmoji($0) }
                view.onMediaRequested = { [weak self] in self?.onMediaRequested($0) }
                view.onBackspace = { [weak self] in self?.view?.deleteBackward() }
                picker = view
                pickerKeyboard = AxoojiKeyboardView(picker: view)
            }
            view.inputView = pickerKeyboard
            picker?.isActive = true
        }
        view.emojiToggle?.pickerOpen = view.inputView != nil
        if view.isFirstResponder { view.reloadInputViews() }
        else { view.becomeFirstResponder() }
    }
    func didResignFirstResponder() {
        picker?.isActive = false
    }
    func didBecomeFirstResponder() {
        picker?.isActive = view?.inputView != nil
    }
    func insertEmoji(_ emoji: Axooji) {
        guard let view else { return }
        guard view.isEditable else { return }
        view.unmarkText()
        let entity = EmojiEntity(offset: 0, length: emoji.fallback.unicodeScalars.count, id: emoji.id)
        let doc = EmojiDocument(text: emoji.fallback, entities: [entity])
        let font = view.font ?? AxooTypography.postBody
        replaceSelection(with: EmojiTextMapper.attributed(doc, font: font, color: view.textColor ?? .label, edge: EmojiTextMetrics.composerEmojiEdge(for: font),
                                              paragraphStyle: view.paragraphStyle, preservesFontLineHeight: true))
    }
    private func replaceSelection(with value: NSAttributedString) {
        guard let view else { return }
        guard view.isEditable else { return }
        let incoming = Self.animatedEmojiCount(in: value)
        if incoming > 0 {
            let remaining = Self.animatedEmojiCount(in: view.textStorage)
                - Self.animatedEmojiCount(in: view.textStorage.attributedSubstring(from: view.selectedRange))
            guard remaining + incoming <= PlaybackPolicy.maximumComposerAnimatedEmoji else { return }
        }
        let previous = NSAttributedString(attributedString: view.textStorage)
        let selection = view.selectedRange
        view.undoManager?.registerUndo(withTarget: self) { $0.restore(previous, selection: selection) }
        view.isApplyingDocument = true
        view.textStorage.replaceCharacters(in: view.selectedRange, with: value)
        view.selectedRange = NSRange(location: selection.location + value.length, length: 0)
        view.typingAttributes = [.font: view.font ?? AxooTypography.postBody, .foregroundColor: view.textColor ?? .label, .paragraphStyle: view.paragraphStyle]
        view.isApplyingDocument = false
        publishEdit()
    }
    private static func animatedEmojiCount(in value: NSAttributedString) -> Int {
        var count = 0
        value.enumerateAttribute(.attachment, in: NSRange(location: 0, length: value.length)) { attachment, range, _ in
            if let attachment = attachment as? AnimatedEmojiSpan, attachment.entity.isAnimated {
                count += range.length
            }
        }
        return count
    }
    private func restore(_ value: NSAttributedString, selection: NSRange) {
        guard let view else { return }
        let previous = NSAttributedString(attributedString: view.textStorage)
        let oldSelection = view.selectedRange
        view.undoManager?.registerUndo(withTarget: self) { $0.restore(previous, selection: oldSelection) }
        view.isApplyingDocument = true
        view.textStorage.setAttributedString(value); view.selectedRange = selection
        view.isApplyingDocument = false
        publishEdit()
    }
    private func publishEdit() {
        guard let view else { return }
        view.setNeedsLayout()
        view.delegate?.textViewDidChange?(view)
        NotificationCenter.default.post(name: UITextView.textDidChangeNotification, object: view)
        view.scrollRangeToVisible(view.selectedRange)
    }
    func copy(_ sender: Any?) {
        guard let view else { return }
        let doc = EmojiTextMapper.document(from: view.textStorage.attributedSubstring(from: view.selectedRange))
        var item: [String: Any] = ["public.utf8-plain-text": doc.text]
        if let data = try? JSONEncoder().encode(doc) { item[Self.pasteboardType] = data }
        UIPasteboard.general.items = [item]
    }
    func cut(_ sender: Any?) {
        guard let view, view.isEditable else { return }
        copy(sender)
        replaceSelection(with: NSAttributedString(string: ""))
    }
    func paste(_ sender: Any?) {
        guard let view else { return }
        guard view.isEditable else { return }
        view.unmarkText()
        if let data = UIPasteboard.general.data(forPasteboardType: Self.pasteboardType),
           let doc = try? JSONDecoder().decode(EmojiDocument.self, from: data), doc.validEntities.count == doc.entities.count {
            let font = view.font ?? AxooTypography.postBody
            replaceSelection(with: EmojiTextMapper.attributed(doc, font: font, color: view.textColor ?? .label, edge: EmojiTextMetrics.composerEmojiEdge(for: font),
                                                  paragraphStyle: view.paragraphStyle, preservesFontLineHeight: true))
        } else if let text = UIPasteboard.general.string { view.insertText(text) }
    }
}
