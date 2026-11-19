import UIKit

/// All five actions remain on one row, with the title beside its icon.
final class PostEditorToolbar: UIView {
    enum Action: CaseIterable { case photo, poll, gif, sticker, meme }
    var onAction: (Action) -> Void = { _ in }
    private var buttons: [(Action, UIButton, CGFloat)] = []

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = AxooTheme.cardBackground
        layer.cornerRadius = PostEditorStyle.toolbarRadius
        layer.cornerCurve = .continuous
        layer.borderWidth = 1
        clipsToBounds = true
        let tools: [(Action, String, AxooIcon, String)] = [
            (.photo, "Foto", .photo, "editor-photo"), (.poll, "Sondaggio", .poll, "editor-poll"),
            (.gif, "GIF", .gif, "editor-gif"), (.sticker, "Sticker", .sticker, "editor-sticker"),
            (.meme, "Meme", .tearsOfJoy, "editor-meme")
        ]
        for (action, title, icon, identifier) in tools {
            let button = PostEditorToolButton(type: .custom)
            button.setTitle(title, for: .normal)
            button.setImage(icon.image(pointSize: 18), for: .normal)
            button.titleLabel?.font = AxooTypography.editorTool
            button.titleLabel?.numberOfLines = 1
            button.titleLabel?.textAlignment = .left
            button.titleLabel?.adjustsFontSizeToFitWidth = true
            button.titleLabel?.minimumScaleFactor = 9.0 / 11.0
            button.accessibilityLabel = title
            button.accessibilityIdentifier = identifier
            button.addAction(UIAction { [weak self] _ in self?.onAction(action) }, for: .touchUpInside)
            addSubview(button)
            let titleWidth = (title as NSString).size(withAttributes: [.font: AxooTypography.editorTool]).width
            buttons.append((action, button, ceil(titleWidth)))
        }
        accessibilityIdentifier = "editor-media-toolbar"
        configure(hasPoll: false, media: nil, enabled: true, pollEnabled: true)
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    func configure(hasPoll: Bool, media: PostEditorMedia.Kind?, enabled: Bool, pollEnabled: Bool) {
        layer.borderColor = AxooTheme.cardBorder.cgColor
        for (action, button, _) in buttons {
            let selected: Bool
            switch action {
            case .poll: selected = hasPoll
            case .photo: selected = media == .photo
            case .gif: selected = media == .gif
            case .sticker: selected = media == .sticker
            case .meme: selected = media == .meme
            }
            button.isEnabled = enabled && (action != .poll || pollEnabled)
            button.alpha = button.isEnabled ? 1 : 0.4
            button.tintColor = selected ? AxooTheme.accent : AxooTheme.textSecondary
            button.setTitleColor(button.tintColor, for: .normal)
            button.isSelected = selected
        }
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        let total = buttons.reduce(CGFloat(0)) { $0 + $1.2 }
        let available = max(0, bounds.width - 8)
        let fixedWidth = 27 * CGFloat(buttons.count)
        let titleScale = min(1, max(0, available - fixedWidth) / max(1, total))
        let spacing = max(0, available - fixedWidth - total * titleScale) / CGFloat(max(1, buttons.count - 1))
        var x: CGFloat = 4
        for (_, button, titleWidth) in buttons {
            let width = 27 + titleWidth * titleScale
            button.frame = CGRect(x: x, y: 0, width: width, height: bounds.height)
            x += width + spacing
        }
    }
}

private final class PostEditorToolButton: UIButton {
    override func imageRect(forContentRect contentRect: CGRect) -> CGRect {
        CGRect(x: 3, y: contentRect.midY - 9, width: 18, height: 18)
    }
    override func titleRect(forContentRect contentRect: CGRect) -> CGRect {
        CGRect(x: 24, y: 0, width: max(0, contentRect.width - 27), height: contentRect.height)
    }
}
