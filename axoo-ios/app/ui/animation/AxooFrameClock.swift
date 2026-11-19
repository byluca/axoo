import UIKit

/// Weak display-link target avoids retaining a removed component through its timer.
@MainActor
final class AxooFrameClock {
    @MainActor private final class Target: NSObject {
        weak var owner: AxooFrameClock?
        @objc func tick(_ link: CADisplayLink) { owner?.onFrame(link.timestamp) }
    }
    var onFrame: (CFTimeInterval) -> Void = { _ in }
    private var link: CADisplayLink?
    private let target = Target()
    func start() {
        guard link == nil else { return }
        target.owner = self
        let link = CADisplayLink(target: target, selector: #selector(Target.tick(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 60, preferred: 60)
        link.add(to: .main, forMode: .common)
        self.link = link
    }
    func stop() { link?.invalidate(); link = nil }
    deinit { link?.invalidate() }
}

extension UIView {
    var axooIsVisible: Bool {
        guard let window = window, UIApplication.shared.applicationState == .active else { return false }
        var visible = convert(bounds, to: window).intersection(window.bounds)
        var ancestor: UIView? = self
        while let node = ancestor {
            if node.isHidden || node.alpha < 0.01 { return false }
            if node.clipsToBounds { visible = visible.intersection(node.convert(node.bounds, to: window)) }
            ancestor = node.superview
        }
        return !visible.isNull && !visible.isEmpty
    }
}

/// Hosts call updateAnimationVisibility while scrolling and when their page becomes active.
class AxooAnimatedDrawingView: AxooDrawingView {
    var animationEnabled = true { didSet { updateAnimationVisibility() } }
    private let clock = AxooFrameClock()
    override init(frame: CGRect) {
        super.init(frame: frame)
        clock.onFrame = { [weak self] time in
            guard let self = self else { return }
            self.updateAnimationVisibility()
            if self.axooIsVisible { self.animationFrame(time) }
        }
        for name in [UIApplication.didBecomeActiveNotification, UIApplication.willResignActiveNotification] {
            NotificationCenter.default.addObserver(self, selector: #selector(visibilityChanged), name: name, object: nil)
        }
    }
    deinit { NotificationCenter.default.removeObserver(self) }
    @objc private func visibilityChanged() { updateAnimationVisibility() }
    override func didMoveToWindow() { super.didMoveToWindow(); updateAnimationVisibility() }
    override func layoutSubviews() { super.layoutSubviews(); updateAnimationVisibility() }
    func updateAnimationVisibility() {
        if animationEnabled && axooIsVisible { clock.start() }
        else { clock.stop(); setNeedsDisplay() }
    }
    func animationFrame(_ time: CFTimeInterval) { setNeedsDisplay() }
}
