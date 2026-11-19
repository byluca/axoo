import UIKit

/// Android's CoreGraphics mascot; UIKit owns the gesture, inset and settling animation.
@MainActor
final class AxooRefresh: UIRefreshControl {
    private weak var scrollView: UIScrollView?
    private let indicator = AxooRefreshIndicator(frame: .zero)
    private var restingInsetTop: CGFloat
    private var requestInFlight = false
    private var closing = false
    private var armed = false
    private var userInitiated = false
    private static let activationDistance: CGFloat = 60
    fileprivate static let spinDuration: CFTimeInterval = 0.760
    var isAdjustingScroll: Bool { isRefreshing || closing }
    var isVisible = true {
        didSet { isHidden = !isVisible; update() }
    }

    init(scrollView: UIScrollView, onRefresh: @escaping () -> Void) {
        self.scrollView = scrollView
        restingInsetTop = scrollView.adjustedContentInset.top
        super.init()
        tintColor = .clear
        backgroundColor = .clear
        clipsToBounds = false
        indicator.isUserInteractionEnabled = false
        addSubview(indicator)
        scrollView.alwaysBounceVertical = true
        scrollView.refreshControl = self
        scrollView.panGestureRecognizer.addTarget(self, action: #selector(pullEnded(_:)))
        addAction(UIAction { [weak self] _ in
            guard let self else { return }
            guard self.isEnabled, self.isVisible else { self.endRefreshing(); return }
            guard !self.requestInFlight else { return }
            self.requestInFlight = true
            if self.userInitiated { Haptics.vibrate() }
            self.userInitiated = false
            self.update()
            onRefresh()
        }, for: .valueChanged)

    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    @objc private func pullEnded(_ gesture: UIPanGestureRecognizer) {
        if gesture.state == .cancelled || gesture.state == .failed {
            armed = false
            userInitiated = false
            return
        }
        guard gesture.state == .ended else { return }
        guard armed, isEnabled, isVisible, !requestInFlight, !isRefreshing else { armed = false; return }
        armed = false
        userInitiated = true
        beginRefreshing()
        sendActions(for: .valueChanged)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        update()
    }

    override func beginRefreshing() {
        super.beginRefreshing()
        update()
    }

    override func endRefreshing() {
        guard isRefreshing || requestInFlight else { return }
        // Like Android, close as soon as loading finishes, without a minimum spin time.
        closing = true
        requestInFlight = false
        super.endRefreshing()
        update()
    }

    /// Called by the list's scroll delegate. Only the drawing follows the offset;
    /// never write contentOffset/contentInset while UIKit is settling the refresh.
    func update() {
        guard let scrollView else { return }
        let spinning = isRefreshing || requestInFlight
        if !spinning && !closing { restingInsetTop = scrollView.adjustedContentInset.top }
        let pull = max(0, -scrollView.contentOffset.y - restingInsetTop)
        if scrollView.isDragging && !spinning && !closing {
            armed = pull >= Self.activationDistance
            userInitiated = armed
        }
        if spinning { closing = false }
        else if closing && pull < 0.5 { closing = false }

        // Cover the exposed strip, including overscroll beyond the control's own
        // height. The 48 pt mascot stays 6 pt below its top, as in AxooRefreshLayout.
        let exposed = CGRect(x: scrollView.bounds.minX,
                             y: scrollView.contentOffset.y + restingInsetTop,
                             width: scrollView.bounds.width, height: pull)
        indicator.frame = convert(exposed, from: scrollView)
        indicator.isHidden = !isVisible || (!isEnabled && !spinning && !closing) || pull < 0.5
        indicator.render(progress: min(1, pull / 60), spinning: spinning || closing)
    }
}

/// Port of AxooRefreshSpinnerView: the existing painter supplies the vector geometry.
private final class AxooRefreshIndicator: AxooAnimatedDrawingView {
    private var progress: CGFloat = 0
    private var spinning = false
    private var startedAt: CFTimeInterval = 0
    private var phase: CGFloat = 0
    private var startRotation: CGFloat = 0

    override init(frame: CGRect) {
        super.init(frame: frame)
        animationEnabled = false
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    func render(progress: CGFloat, spinning: Bool) {
        if spinning && !self.spinning {
            startRotation = self.progress * .pi * 2
            startedAt = CACurrentMediaTime()
            phase = 0
        }
        self.progress = progress
        self.spinning = spinning
        animationEnabled = spinning
        setNeedsDisplay()
    }

    override func animationFrame(_ time: CFTimeInterval) {
        phase = CGFloat((max(0, time - startedAt) / AxooRefresh.spinDuration).truncatingRemainder(dividingBy: 1))
        setNeedsDisplay()
    }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        context.setFillColor(UIColor(rgb: 0xFF00BF).cgColor)
        context.fill(bounds)

        let wave: CGFloat = sin(spinning ? phase * .pi * 2 : progress * .pi)
        let angle: CGFloat = (spinning
            ? startRotation + phase * .pi * 2 + wave * 5.5 * .pi / 180
            : progress * .pi * 2)
        let scale: CGFloat = spinning ? 1 : 0.72 + progress * 0.28
        let opacity: CGFloat = spinning ? 1 : min(1, max(0, (progress - 0.08) / 0.52))
        let verticalOffset: CGFloat = spinning ? wave * 0.45 : 0
        context.saveGState()
        context.setAlpha(opacity)
        context.translateBy(x: bounds.midX, y: 30)
        context.scaleBy(x: scale, y: scale)
        context.rotate(by: angle)
        AxooMascotCanvasPainter.draw(in: context,
                                    bounds: CGRect(x: -24, y: -24 + verticalOffset, width: 48, height: 48),
                                    squash: wave)
        context.restoreGState()
    }
}
