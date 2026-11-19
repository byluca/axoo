import UIKit

/// One native material surface for floating navigation and media actions.
class AxooPillView: UIVisualEffectView {
    enum Surface { case material, white }
    private let surface: Surface
    init(surface: Surface = .material) {
        self.surface = surface
        super.init(effect: surface == .material ? UIBlurEffect(style: .systemMaterial) : nil)
        if surface == .white {
            backgroundColor = AxooTheme.cardBackground
            contentView.backgroundColor = AxooTheme.cardBackground
            layer.borderColor = AxooTheme.cardBorder.cgColor
            layer.borderWidth = 1
        }
        #if compiler(>=6.2)
        if #available(iOS 26.0, *), surface == .material {
            effect = UIGlassEffect()
        }
        #endif
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        if surface == .white { layer.borderColor = AxooTheme.cardBorder.resolvedColor(with: traitCollection).cgColor }
        #if compiler(>=6.2)
        if #available(iOS 26.0, *), surface == .material { return }
        #endif
        layer.cornerRadius = bounds.height / 2
        layer.cornerCurve = .continuous
        clipsToBounds = true
        if surface == .white {
            contentView.layer.cornerRadius = bounds.height / 2
            contentView.layer.cornerCurve = .continuous
            contentView.clipsToBounds = true
        }
    }
}

extension UISegmentedControl {
    static func axoo(items: [Any], showsSelectionIndicator: Bool = true) -> UISegmentedControl {
        let control = UISegmentedControl(items: items)
        control.selectedSegmentIndex = 0
        control.selectedSegmentTintColor = showsSelectionIndicator ? AxooTheme.selection : .clear
        control.backgroundColor = .clear
        let clear = UIGraphicsImageRenderer(size: CGSize(width: 1, height: 1)).image { _ in }
        let selected = UIGraphicsImageRenderer(size: CGSize(width: 16, height: 16)).image { _ in
            if showsSelectionIndicator {
                AxooTheme.selection.setFill()
                UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: 16, height: 16), cornerRadius: 6).fill()
            }
        }.resizableImage(withCapInsets: UIEdgeInsets(top: 6, left: 6, bottom: 6, right: 6))
        control.setBackgroundImage(clear, for: .normal, barMetrics: .default)
        control.setBackgroundImage(clear, for: .highlighted, barMetrics: .default)
        control.setBackgroundImage(selected, for: .selected, barMetrics: .default)
        control.setBackgroundImage(selected, for: [.selected, .highlighted], barMetrics: .default)
        control.setDividerImage(clear, forLeftSegmentState: .normal, rightSegmentState: .normal, barMetrics: .default)
        control.tintColor = AxooTheme.textPrimary
        control.setTitleTextAttributes([.font: AxooTypography.postAuthor, .foregroundColor: AxooTheme.textPrimary], for: .normal)
        control.setTitleTextAttributes([.foregroundColor: AxooTheme.accent], for: .selected)
        return control
    }
}
