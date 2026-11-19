import UIKit

/// Shared hold/slide/release gesture. Callers supply content and keep normal tap selection.
@MainActor
final class HoldMediaPreview: NSObject {
    struct Content {
        let key: String
        let aspectRatio: CGFloat
        var maximumSize: CGFloat = 360
        let makeView: () -> UIView
    }
    private static weak var current: HoldMediaPreview?
    private weak var source: UIView?
    private let resolve: (CGPoint) -> Content?
    private let onVisibility: (Bool) -> Void
    private var overlay: Surface?
    private var key: String?

    init(source: UIView, resolve: @escaping (CGPoint) -> Content?, onVisibility: @escaping (Bool) -> Void) {
        self.source = source
        self.resolve = resolve
        self.onVisibility = onVisibility
        super.init()
        Haptics.prepare()
        let hold = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        hold.minimumPressDuration = 0.35
        hold.cancelsTouchesInView = true
        source.addGestureRecognizer(hold)
        NotificationCenter.default.addObserver(self, selector: #selector(dismiss), name: UIApplication.willResignActiveNotification, object: nil)
    }
    deinit {
        NotificationCenter.default.removeObserver(self)
        let surface = overlay
        Task { @MainActor in
            surface?.removeFromSuperview()
        }
    }

    @objc private func handle(_ hold: UILongPressGestureRecognizer) {
        guard let source else { return }
        switch hold.state {
        case .began:
            guard let content = resolve(hold.location(in: source)), let window = source.window else { return }
            Self.current?.dismiss()
            let surface = Surface(frame: window.bounds)
            surface.onDetached = { [weak self] in self?.dismiss() }
            overlay = surface
            key = content.key
            Self.current = self
            surface.show(content)
            window.addSubview(surface)
            surface.animateEntrance()
            Haptics.vibrate()
            onVisibility(true)
        case .changed:
            guard let content = resolve(hold.location(in: source)), content.key != key else { return }
            key = content.key
            overlay?.show(content)
        case .ended, .cancelled, .failed: dismiss()
        default: break
        }
    }

    @objc func dismiss() {
        guard let surface = overlay else { return }
        overlay = nil
        key = nil
        if Self.current === self { Self.current = nil }
        surface.removeFromSuperview()
        onVisibility(false)
    }

    private final class Surface: UIView {
        private let card = UIView()
        private var content: UIView?
        private var aspect: CGFloat = 1
        private var maximumSize: CGFloat = 360
        var onDetached: (() -> Void)?
        override init(frame: CGRect) {
            super.init(frame: frame)
            accessibilityIdentifier = "media-hold-preview"
            backgroundColor = UIColor.black.withAlphaComponent(0.6)
            isUserInteractionEnabled = false
            autoresizingMask = [.flexibleWidth, .flexibleHeight]
            card.backgroundColor = .clear
            card.layer.cornerRadius = 24
            card.clipsToBounds = true
            addSubview(card)
        }
        @available(*, unavailable) required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
        func show(_ value: Content) {
            maximumSize = value.maximumSize
            aspect = value.aspectRatio.isFinite && value.aspectRatio > 0 ? value.aspectRatio : 1
            content?.removeFromSuperview()
            let view = value.makeView()
            content = view
            view.layer.cornerRadius = 20
            view.layer.cornerCurve = .continuous
            view.clipsToBounds = true
            card.addSubview(view)
            setNeedsLayout()
        }
        func animateEntrance() {
            guard !UIAccessibility.isReduceMotionEnabled else { return }
            layoutIfNeeded()
            alpha = 0
            card.transform = CGAffineTransform(scaleX: 0.88, y: 0.88)
            UIView.animate(withDuration: 0.22, delay: 0, options: [.curveEaseOut, .beginFromCurrentState]) {
                self.alpha = 1
                self.card.transform = .identity
            }
        }
        override func layoutSubviews() {
            super.layoutSubviews()
            let top = safeAreaInsets.top
            let keyboardTop = window?.keyboardLayoutGuide.layoutFrame.minY ?? bounds.height
            let bottom = min(bounds.height - safeAreaInsets.bottom, keyboardTop > top + 96 ? keyboardTop : bounds.height)
            let maxWidth = max(1, min(bounds.width - 48, maximumSize) - 32)
            let maxHeight = max(1, min(bottom - top - 96, 520) - 32)
            let width = min(maxWidth, maxHeight * aspect), height = min(maxHeight, width / aspect)
            card.frame = CGRect(x: (bounds.width - width - 32) / 2, y: max(top, top + (bottom - top - height - 32) / 2), width: width + 32, height: height + 32)
            content?.frame = card.bounds.insetBy(dx: 16, dy: 16)
        }
        override func didMoveToWindow() { super.didMoveToWindow(); if window == nil { onDetached?() } }
    }
}
