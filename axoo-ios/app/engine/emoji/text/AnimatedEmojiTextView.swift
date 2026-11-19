import UIKit

/// Displays read-only comment text and emoji animations.
final class AnimatedEmojiTextView: UIView {
    private let content = EmojiTextView(readOnly: true)
    var font: UIFont {
        get { content.font ?? AxooTypography.postBody }
        set { content.font = newValue }
    }
    var textColor: UIColor {
        get { content.textColor ?? .label }
        set { content.textColor = newValue }
    }
    init() {
        super.init(frame: .zero)
        addSubview(content)
        isUserInteractionEnabled = true
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    func setDocument(_ value: EmojiDocument, enlargeEmojiOnly: Bool) {
        content.setDocument(value, enlargeEmojiOnly: enlargeEmojiOnly)
    }
    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        content.point(inside: convert(point, to: content), with: event)
    }
    override func sizeThatFits(_ size: CGSize) -> CGSize { content.sizeThatFits(size) }
    override func layoutSubviews() { super.layoutSubviews(); content.frame = bounds }
}
