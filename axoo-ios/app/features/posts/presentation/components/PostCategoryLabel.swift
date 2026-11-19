import UIKit

final class PostCategoryLabel: AxooDrawingView {
    var category = "" { didSet { configure(); invalidateIntrinsicContentSize(); setNeedsLayout(); setNeedsDisplay() } }
    private let label = UILabel.axoo(font: AxooTypography.postCategory)
    private let icon = UIImageView()

    init() {
        super.init(frame: .zero)
        backgroundColor = .clear
        isOpaque = false
        addSubview(label)
        addSubview(icon)
        icon.contentMode = .scaleAspectFit
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    private func configure() {
        label.text = category.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        label.font = AxooTypography.postCategory
        label.textColor = UIColor(rgb: appearance.foreground)
        icon.image = appearance.icon?.image
        icon.tintColor = label.textColor
    }
    override func sizeThatFits(_ size: CGSize) -> CGSize {
        let iconWidth: CGFloat = appearance.icon == nil ? 0 : 18
        let text = label.sizeThatFits(CGSize(width: max(1, size.width - 18 - iconWidth), height: .greatestFiniteMagnitude))
        return CGSize(width: min(size.width, text.width + 18 + iconWidth), height: text.height + 8)
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        let iconWidth: CGFloat = appearance.icon == nil ? 0 : 18
        icon.frame = CGRect(x: 9, y: (bounds.height - 13) / 2, width: 13, height: 13)
        label.frame = CGRect(x: 9 + iconWidth, y: 4, width: max(0, bounds.width - 18 - iconWidth), height: bounds.height - 8)
    }
    override func draw(_ rect: CGRect) {
        UIColor(rgb: appearance.background).setFill()
        UIBezierPath(roundedRect: bounds, cornerRadius: min(14, bounds.height / 2)).fill()
    }
    // One palette for the card and the editor category picker.
    private var appearance: (background: UInt32, foreground: UInt32, icon: AxooIcon?) {
        Self.appearance(for: category)
    }
    static func appearance(for category: String) -> (background: UInt32, foreground: UInt32, icon: AxooIcon?) {
        switch category.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "meme": return (0xE4D1FF, 0x6A1FB5, .meme)
        case "spotted": return (0xFFC7E6, 0xAD1070, .spotted)
        case "esami": return (0xC9E4FF, 0x1060B0, .exams)
        case "confessioni": return (0xCBEFE2, 0x19795A, .confessions)
        case "party": return (0xFFF2A8, 0x7A5200, .party)
        case "dell'andro", "fraccacreta": return (0xFFB38A, 0x8B2F13, .residence)
        default: return (0xF0ECF2, 0x514A55, nil)
        }
    }
}
