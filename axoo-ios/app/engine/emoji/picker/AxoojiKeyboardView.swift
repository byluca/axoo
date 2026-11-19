import UIKit

/// UIKit sizes this input view to match the system keyboard, also on rotation.
/// The picker fills the surface and keeps its controls inside the safe area.
final class AxoojiKeyboardView: UIInputView {
    private let picker: EmojiPanelView
    private var entranceAnimator: UIViewPropertyAnimator?

    init(picker: EmojiPanelView) {
        self.picker = picker
        // Keep the host transparent so the system keyboard material does not
        // fill the rounded corners beneath the picker and its shadow.
        super.init(frame: .zero, inputViewStyle: .default)
        // Flexible height lets UIKit choose the native keyboard height instead
        // of a fixed height or a self-sizing height supplied by the picker.
        autoresizingMask = [.flexibleWidth, .flexibleHeight]
        allowsSelfSizing = false
        backgroundColor = .clear
        clipsToBounds = false
        addSubview(picker)
        picker.pinEdges(to: self)
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        overrideUserInterfaceStyle = AxooTheme.interfaceStyle
        entranceAnimator?.stopAnimation(true)
        entranceAnimator = nil
        picker.transform = .identity
        picker.alpha = 1
        guard window != nil, !UIAccessibility.isReduceMotionEnabled, UIView.areAnimationsEnabled else { return }

        picker.transform = CGAffineTransform(translationX: 0, y: 12)
        picker.alpha = 0.85
        let animator = UIViewPropertyAnimator(duration: 0.22, timingParameters: AxooInterpolators.enter)
        animator.addAnimations { [weak self] in
            self?.picker.transform = .identity
            self?.picker.alpha = 1
        }
        entranceAnimator = animator
        animator.startAnimation()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
}
