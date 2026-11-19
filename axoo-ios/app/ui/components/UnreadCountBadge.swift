import UIKit

final class UnreadCountBadge: AxooDrawingView {
    var count = 0 { didSet { isHidden = count <= 0; setNeedsDisplay() } }
    override init(frame: CGRect) {
        super.init(frame: frame)
        isHidden = true
        isOpaque = false
        backgroundColor = .clear
        isUserInteractionEnabled = false
    }
    convenience init() { self.init(frame: .zero) }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    private var text: NSString { (count > 99 ? "99+" : String(max(0, count))) as NSString }
    private var attributes: [NSAttributedString.Key: Any] {
        [.font: AxooTypography.badge, .foregroundColor: UIColor.white]
    }
    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: ceil(text.size(withAttributes: attributes).width) + 10, height: 16)
    }
    override func draw(_ rect: CGRect) {
        UIColor(rgb: 0xFF3B30).setFill()
        UIBezierPath(roundedRect: bounds, cornerRadius: bounds.height / 2).fill()
        let size = text.size(withAttributes: attributes)
        text.draw(at: CGPoint(x: (bounds.width - size.width) / 2, y: (bounds.height - size.height) / 2), withAttributes: attributes)
    }
}

