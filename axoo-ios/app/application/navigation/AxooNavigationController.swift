import UIKit

/// Keep UIKit's interactive return available with our custom arrow buttons.
final class AxooNavigationController: UINavigationController, UIGestureRecognizerDelegate {
    override func viewDidLoad() {
        super.viewDidLoad()
        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = AxooTheme.cardBackground
        appearance.shadowColor = .clear
        appearance.titleTextAttributes = [.font: AxooTypography.statusTitle, .foregroundColor: AxooTheme.textPrimary]
        navigationBar.isTranslucent = false
        navigationBar.tintColor = AxooTheme.textPrimary
        navigationBar.standardAppearance = appearance
        navigationBar.scrollEdgeAppearance = appearance
        navigationBar.compactAppearance = appearance
        navigationBar.compactScrollEdgeAppearance = appearance
        interactivePopGestureRecognizer?.delegate = self
    }

    override func pushViewController(_ viewController: UIViewController, animated: Bool) {
        if !viewControllers.isEmpty { viewController.hidesBottomBarWhenPushed = true }
        super.pushViewController(viewController, animated: animated)
    }

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard viewControllers.count > 1, transitionCoordinator == nil,
              presentedViewController == nil else { return false }
        return (topViewController as? AxooViewController)?.allowsBackSwipe ?? true
    }
}
