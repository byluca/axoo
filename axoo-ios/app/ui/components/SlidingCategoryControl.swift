import UIKit

/// Shared sliding selection for text categories and avatar icons.
final class SlidingCategoryControl: UIControl {
    private let indicator = UIView()
    private var buttons: [UIButton] = []
    private var titles: [String] = []
    private let indicatorSize: CGSize?
    private var laidOut = false
    var usesCompactPillStyle = false {
        didSet {
            buttons.forEach { $0.titleLabel?.font = usesCompactPillStyle ? AxooTypography.postAuthor.withSize(13) : AxooTypography.postAuthor }
            invalidateIntrinsicContentSize()
            setNeedsLayout()
        }
    }
    var apportionsSegmentWidthsByContent = false { didSet { invalidateIntrinsicContentSize(); setNeedsLayout() } }
    var selectedSegmentIndex = 0 {
        didSet {
            guard oldValue != selectedSegmentIndex else { return }
            updateSelection()
            setNeedsLayout()
            if laidOut {
                UIView.animate(withDuration: 0.22, delay: 0, options: [.beginFromCurrentState, .curveEaseInOut]) {
                    self.layoutIfNeeded()
                }
            }
        }
    }
    override var isEnabled: Bool {
        didSet { buttons.forEach { $0.isEnabled = isEnabled }; alpha = isEnabled ? 1 : 0.55 }
    }
    convenience init(items: [String]) {
        self.init(buttons: items.map { title in
            let button = UIButton(type: .custom)
            button.setTitle(title, for: .normal)
            button.titleLabel?.font = AxooTypography.postAuthor
            return button
        })
    }
    init(buttons: [UIButton], indicatorSize: CGSize? = nil) {
        self.buttons = buttons
        titles = buttons.map { $0.title(for: .normal) ?? "" }
        self.indicatorSize = indicatorSize
        super.init(frame: .zero)
        indicator.backgroundColor = AxooTheme.selection
        indicator.layer.cornerRadius = indicatorSize.map { min($0.width, $0.height) / 2 } ?? 8
        indicator.isUserInteractionEnabled = false
        addSubview(indicator)
        for (index, button) in buttons.enumerated() {
            button.addAction(UIAction { [weak self] _ in
                guard let self, self.isEnabled, self.selectedSegmentIndex != index else { return }
                self.selectedSegmentIndex = index
                self.sendActions(for: .valueChanged)
            }, for: .touchUpInside)
            addSubview(button)
        }
        updateSelection()
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    func setImage(_ image: UIImage?, forSegmentAt index: Int) {
        guard buttons.indices.contains(index) else { return }
        buttons[index].setImage(image, for: .normal)
        buttons[index].setTitle(image == nil ? titles[index] : nil, for: .normal)
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }
    private func updateSelection() {
        for (index, button) in buttons.enumerated() {
            button.isSelected = index == selectedSegmentIndex
            button.setTitleColor(button.isSelected ? AxooTheme.accent : AxooTheme.textPrimary, for: .normal)
        }
    }
    private var widths: [CGFloat] {
        buttons.map { max(44, ceil($0.intrinsicContentSize.width) + 20) }
    }
    override var intrinsicContentSize: CGSize {
        CGSize(width: widths.reduce(0, +), height: usesCompactPillStyle ? 36 : max(44, AxooTypography.postAuthor.lineHeight + 16))
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        guard !buttons.isEmpty else { return }
        let natural = widths
        let inset: CGFloat = usesCompactPillStyle ? 5 : 0
        let contentWidth = max(0, bounds.width - inset * 2)
        let extra = max(0, contentWidth - natural.reduce(0, +)) / CGFloat(buttons.count)
        var x: CGFloat = inset
        for (index, button) in buttons.enumerated() {
            let width = apportionsSegmentWidthsByContent ? natural[index] + extra : contentWidth / CGFloat(buttons.count)
            button.frame = CGRect(x: x, y: 0, width: width, height: bounds.height)
            x += width
        }
        if buttons.indices.contains(selectedSegmentIndex) {
            let frame = buttons[selectedSegmentIndex].frame
            if let indicatorSize {
                indicator.frame = CGRect(x: frame.midX - indicatorSize.width / 2,
                                         y: frame.midY - indicatorSize.height / 2,
                                         width: indicatorSize.width, height: indicatorSize.height)
            } else {
                indicator.frame = frame.insetBy(dx: usesCompactPillStyle ? 0 : 2, dy: usesCompactPillStyle ? 5 : 4)
                indicator.layer.cornerRadius = usesCompactPillStyle ? indicator.bounds.height / 2 : 8
            }
        }
        laidOut = bounds.width > 0
    }
}
