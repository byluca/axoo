import UIKit

/// Android's whole-number reel: increasing values enter from below, decreasing from above.
enum RollingNumberDrawing {
    static func draw(old: String, current: String, increasing: Bool, progress: CGFloat,
                     font: UIFont, color: UIColor, in bounds: CGRect, context: CGContext) {
        let p = min(1, max(0, progress))
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        let distance = font.lineHeight + 4
        let direction: CGFloat = increasing ? -1 : 1
        func text(_ value: String, offset: CGFloat) {
            let size = (value as NSString).size(withAttributes: attributes)
            (value as NSString).draw(at: CGPoint(x: bounds.midX - size.width / 2,
                                                y: bounds.midY - size.height / 2 + offset), withAttributes: attributes)
        }
        context.saveGState()
        context.clip(to: bounds)
        if p >= 1 || old == current { text(current, offset: 0) }
        else {
            text(old, offset: direction * distance * p)
            text(current, offset: direction * distance * (p - 1))
        }
        context.restoreGState()
    }
}

final class RollingNumberView: UILabel {
    private let clock = AxooFrameClock()
    private var identity: String?
    private var value = 0
    private var previous = ""
    private var increasing = true
    private var started: CFTimeInterval = 0
    private var progress: CGFloat = 1
    override init(frame: CGRect) {
        super.init(frame: frame)
        textAlignment = .center
        clock.onFrame = { [weak self] time in
            guard let self = self else { return }
            guard self.axooIsVisible else { self.finishAnimation(); return }
            let linear = min(1, max(0, (time - self.started) / 0.24))
            self.progress = CGFloat(1 - pow(1 - linear, 3))
            self.setNeedsDisplay()
            if linear >= 1 { self.finishAnimation() }
        }
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    func configure(value: Int, identity: String) {
        let next = String(value)
        guard self.identity != identity || self.value != value else { return }
        let animate = self.identity == identity && axooIsVisible
        // Retarget from the previous logical value; never animate a reused cell's unrelated record.
        previous = text ?? next
        increasing = value > self.value
        self.identity = identity
        self.value = value
        text = next
        finishAnimation()
        if animate {
            progress = 0
            started = CACurrentMediaTime()
            clock.start()
        }
    }
    func finishAnimation() { clock.stop(); progress = 1; setNeedsDisplay() }
    override func didMoveToWindow() { super.didMoveToWindow(); if window == nil { finishAnimation() } }
    override func drawText(in rect: CGRect) {
        guard progress < 1, let context = UIGraphicsGetCurrentContext() else { super.drawText(in: rect); return }
        var drawingFont = font ?? AxooTypography.postScore
        let widest = max((previous as NSString).size(withAttributes: [.font: drawingFont]).width,
                         ((text ?? "") as NSString).size(withAttributes: [.font: drawingFont]).width)
        if adjustsFontSizeToFitWidth && widest > rect.width {
            drawingFont = drawingFont.withSize(drawingFont.pointSize * max(minimumScaleFactor, rect.width / widest))
        }
        RollingNumberDrawing.draw(old: previous, current: text ?? "", increasing: increasing,
                                  progress: progress, font: drawingFont, color: textColor ?? .label,
                                  in: rect, context: context)
    }
}
