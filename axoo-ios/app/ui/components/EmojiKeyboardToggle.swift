import UIKit

/// Uses the exact smiling.svg / keyboard.svg pair from Android.
final class EmojiKeyboardToggle: UIButton {
    var pickerOpen = false { didSet { updateIcon() } }
    var onToggle: () -> Void = {}
    private let iconSize: CGFloat
    private let hitSize: CGFloat

    init(iconSize: CGFloat = 28, hitSize: CGFloat = 44) {
        self.iconSize = iconSize
        self.hitSize = hitSize
        super.init(frame: .zero)
        tintColor = AxooTheme.textSecondary
        accessibilityIdentifier = "emoji-keyboard-toggle"
        addAction(UIAction { [weak self] _ in self?.onToggle() }, for: .touchUpInside)
        updateIcon()
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    override var intrinsicContentSize: CGSize { CGSize(width: hitSize, height: hitSize) }

    private func updateIcon() {
        setImage((pickerOpen ? AxooIcon.keyboard : .emoji).image(pointSize: iconSize), for: .normal)
        isSelected = pickerOpen
    }
}
