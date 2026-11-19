import UIKit

/// Floating delete control shared by the post and comment emoji keyboards.
final class EmojiBackspaceButton: UIButton {
    var onBackspace: () -> Void = {}
    private var repeatTimer: Timer?
    private var ticks = 0
    private var repeating = false

    init() {
        super.init(frame: .zero)
        backgroundColor = .clear
        isOpaque = false
        contentMode = .redraw
        tintColor = AxooTheme.textPrimary
        setImage(AxooIcon.backspace.image(pointSize: 23), for: .normal)
        accessibilityIdentifier = "axooji-backspace"
        addAction(UIAction { [weak self] _ in self?.startRepeating() }, for: .touchDown)
        addAction(UIAction { [weak self] _ in self?.stopRepeating() },
                  for: [.touchUpInside, .touchUpOutside, .touchCancel, .touchDragExit])
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    deinit { repeatTimer?.invalidate() }

    override var isHighlighted: Bool {
        didSet { transform = isHighlighted ? CGAffineTransform(scaleX: 0.94, y: 0.94) : .identity }
    }
    override var isEnabled: Bool {
        didSet { if !isEnabled { stopRepeating() } }
    }
    override func imageRect(forContentRect contentRect: CGRect) -> CGRect {
        CGRect(x: contentRect.midX - 11.5, y: contentRect.midY - 10.5, width: 23, height: 23)
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil { stopRepeating() }
    }
    private func startRepeating() {
        stopRepeating()
        guard isEnabled else { return }
        repeating = true
        onBackspace()
        scheduleRepeat(after: 0.3)
    }
    private func scheduleRepeat(after delay: TimeInterval) {
        guard repeating else { return }
        let timer = Timer(timeInterval: delay, repeats: false) { [weak self] _ in
            guard let self, self.repeating, self.isEnabled else { return }
            self.onBackspace()
            self.ticks += 1
            self.scheduleRepeat(after: max(0.05, 0.3 - Double(self.ticks) * 0.025))
        }
        repeatTimer = timer
        RunLoop.main.add(timer, forMode: .common)
    }
    func stopRepeating() {
        repeating = false
        repeatTimer?.invalidate()
        repeatTimer = nil
        ticks = 0
    }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        setNeedsDisplay()
    }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        let radius = max(0, min(bounds.width, bounds.height) / 2 - 6)
        let disc = CGRect(x: bounds.midX - radius, y: bounds.midY + 1 - radius,
                          width: radius * 2, height: radius * 2)
        context.saveGState()
        context.setShadow(offset: CGSize(width: 0, height: 3), blur: 4,
                          color: UIColor.black.withAlphaComponent(0.2).cgColor)
        context.setFillColor(AxooTheme.cardBackground.cgColor)
        context.fillEllipse(in: disc)
        context.restoreGState()
        context.setStrokeColor(AxooTheme.cardBorder.cgColor)
        context.setLineWidth(1)
        context.strokeEllipse(in: disc)
    }
}
