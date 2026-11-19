import UIKit

final class EmojiPickerHeader: UICollectionReusableView {
    let label = UILabel.axoo(font: AxooTypography.emojiCategory, color: AxooTheme.textSecondary)
    override init(frame: CGRect) {
        super.init(frame: frame)
        addSubview(label)
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    override func layoutSubviews() {
        super.layoutSubviews()
        label.frame = CGRect(x: 12, y: 6, width: max(0, bounds.width - 24), height: max(0, bounds.height - 16))
    }
}
