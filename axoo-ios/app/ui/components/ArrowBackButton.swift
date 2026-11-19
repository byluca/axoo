import UIKit

/// The original Android arrow in a shared 44pt touch target.
final class ArrowBackButton: UIButton {
    init(action: @escaping () -> Void) {
        super.init(frame: .zero)
        setImage(AxooIcon.back.image(pointSize: 24), for: .normal)
        tintColor = AxooTheme.textPrimary
        translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            widthAnchor.constraint(equalToConstant: 44),
            heightAnchor.constraint(equalToConstant: 44)
        ])
        addAction(UIAction { _ in action() }, for: .touchUpInside)
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
}

extension UIViewController {
    func installArrowBackButton(testID: String, animated: Bool = true) {
        let button = ArrowBackButton { [weak self] in
            self?.navigationController?.popViewController(animated: animated)
        }
        button.accessibilityIdentifier = testID
        let item = UIBarButtonItem(customView: button)
        item.hideSharedBackground()
        navigationItem.leftBarButtonItem = item
    }
}

extension UIBarButtonItem {
    func hideSharedBackground() {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            hidesSharedBackground = true
        }
        #endif
    }
}
