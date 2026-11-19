import UIKit

final class EmojiCategoryButton: UIButton {
    private let icon = UIImageView()
    override var isSelected: Bool { didSet { alpha = isSelected ? 1 : 0.74 } }

    init(section: EmojiAdapter.Section) {
        super.init(frame: .zero)
        accessibilityLabel = section.title
        if section.id == "recent" {
            icon.image = Bundle.main.url(forResource: "recents", withExtension: "webp", subdirectory: "axooji/category-icons").flatMap { UIImage(contentsOfFile: $0.path)?.withRenderingMode(.alwaysTemplate) }
            icon.tintColor = AxooTheme.textPrimary
        } else if let url = Bundle.main.url(forResource: section.id, withExtension: "png", subdirectory: "axooji/categories") {
            icon.image = UIImage(contentsOfFile: url.path)
        }
        icon.contentMode = .scaleAspectFit
        addSubview(icon)
        alpha = 0.74
        accessibilityIdentifier = "axooji-category-\(section.id)"
        widthAnchor.constraint(equalToConstant: 44).isActive = true
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        icon.frame = CGRect(x: bounds.midX - 11, y: bounds.midY - 11, width: 22, height: 22)
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
}
