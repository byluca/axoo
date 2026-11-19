import UIKit

/// Shared bottom veil for scrolling content. Controls and touches stay above it.
final class BottomFadeView: AxooDrawingView {
    static let height: CGFloat = 72
    private let color: UIColor

    init(color: UIColor = AxooTheme.surface) {
        self.color = color
        super.init(frame: .zero)
        isUserInteractionEnabled = false
        accessibilityIdentifier = "bottom-fade"
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    override var intrinsicContentSize: CGSize {
        CGSize(width: UIView.noIntrinsicMetric, height: Self.height)
    }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext(), bounds.height > 0 else { return }
        let colors = [0x00, 0x60, 0xB0, 0xE8].map {
            color.withAlphaComponent(CGFloat($0) / 255).cgColor
        }
        guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors as CFArray, locations: nil) else { return }
        context.drawLinearGradient(gradient,
            start: CGPoint(x: bounds.midX, y: bounds.minY),
            end: CGPoint(x: bounds.midX, y: bounds.maxY), options: [])
    }
}
