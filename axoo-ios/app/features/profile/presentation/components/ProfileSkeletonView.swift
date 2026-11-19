import UIKit

final class ProfileSkeletonView: AxooDrawingView {
    private let bio: Bool
    init(bio: Bool = false) {
        self.bio = bio
        super.init(frame: .zero)
        isUserInteractionEnabled = false
        accessibilityIdentifier = "profile-skeleton"
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    override var intrinsicContentSize: CGSize { CGSize(width: UIView.noIntrinsicMetric, height: bio ? 58 : 262) }
    override func draw(_ rect: CGRect) {
        let card = UIBezierPath(roundedRect: bounds.insetBy(dx: 0.5, dy: 0.5), cornerRadius: 24)
        AxooTheme.cardBackground.setFill(); card.fill()
        AxooTheme.cardBorder.setStroke(); card.lineWidth = 0.7; card.stroke()
        UIColor.secondaryLabel.withAlphaComponent(0.13).setFill()
        if bio {
            UIBezierPath(roundedRect: CGRect(x: 20, y: 22, width: bounds.width * 0.65 - 20, height: 14), cornerRadius: 7).fill()
            return
        }
        UIBezierPath(ovalIn: CGRect(x: bounds.midX - 66, y: 28, width: 132, height: 132)).fill()
        UIBezierPath(roundedRect: CGRect(x: bounds.midX - 90, y: 172, width: 180, height: 22), cornerRadius: 10).fill()
        UIBezierPath(roundedRect: CGRect(x: bounds.midX - 65, y: 202, width: 130, height: 14), cornerRadius: 7).fill()
    }
}
