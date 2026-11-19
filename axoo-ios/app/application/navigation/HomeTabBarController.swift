import UIKit

/// UIKit owns tab layout, selection and Liquid Glass interactions.
final class HomeTabBarController: UITabBarController, UITabBarControllerDelegate {
    private let creation = UIViewController()
    private let onCreate: () -> Void
    private let notificationsBadge = UnreadCountBadge()

    init(home: UINavigationController, events: UINavigationController, notifications: UINavigationController, profile: UINavigationController, onCreate: @escaping () -> Void) {
        self.onCreate = onCreate
        super.init(nibName: nil, bundle: nil)
        delegate = self
        for (controller, item) in zip([home, events, creation, notifications, profile], HomeNavigationItem.allCases) {
            let icon = item.icon(selected: false).image(pointSize: 24)
            controller.tabBarItem = UITabBarItem(title: item == .create ? "Crea" : item.title,
                image: icon, selectedImage: item.icon(selected: true).image(pointSize: 24))
            controller.tabBarItem.accessibilityIdentifier = "navigation-\(item.rawValue)"
        }
        viewControllers = [home, events, creation, notifications, profile]
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = AxooTheme.background
        let appearance = UITabBarAppearance()
        appearance.configureWithTransparentBackground()
        appearance.backgroundColor = .clear
        appearance.shadowColor = .clear
        tabBar.isTranslucent = true
        tabBar.standardAppearance = appearance
        tabBar.scrollEdgeAppearance = appearance
        tabBar.itemPositioning = .fill
        tabBar.addSubview(notificationsBadge)
        tabBar.tintColor = AxooTheme.textPrimary
        tabBar.unselectedItemTintColor = AxooTheme.textPrimary
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            tabBarMinimizeBehavior = .never
        }
        #endif
    }

    func bindNotificationsCount(_ count: Int) {
        notificationsBadge.count = count
        tabBar.items?[3].accessibilityValue = count > 0 ? "\(count) nuove" : nil
        view.setNeedsLayout()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let size = notificationsBadge.sizeThatFits(tabBar.bounds.size)
        let available = tabBar.bounds.width - tabBar.safeAreaInsets.left - tabBar.safeAreaInsets.right
        let center = tabBar.safeAreaInsets.left + available * 3.5 / 5
        notificationsBadge.frame = CGRect(x: min(tabBar.bounds.width - size.width - 8, center + 9), y: 2,
                                         width: size.width, height: size.height)
        tabBar.bringSubviewToFront(notificationsBadge)
    }

    func tabBarController(_ tabBarController: UITabBarController, shouldSelect viewController: UIViewController) -> Bool {
        guard viewController === creation else { return true }
        onCreate()
        return false
    }
}
