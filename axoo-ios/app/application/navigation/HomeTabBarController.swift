import UIKit

/// UIKit owns tab layout, selection and Liquid Glass interactions.
final class HomeTabBarController: UITabBarController, UITabBarControllerDelegate {
    private let creation = UIViewController()
    private let onCreate: () -> Void
    private let notifications: UINavigationController
    private let notificationsBadge = UnreadCountBadge()

    init(home: UINavigationController, notifications: UINavigationController, profile: UINavigationController, onCreate: @escaping () -> Void) {
        self.onCreate = onCreate
        self.notifications = notifications
        super.init(nibName: nil, bundle: nil)
        delegate = self
        let items: [(controller: UIViewController, title: String, id: String, icon: AxooIcon, selectedIcon: AxooIcon)] = [
            (home, "Home", "home", .home, .homeSelected),
            (creation, "Crea", "create", .create, .createSelected),
            (notifications, "Notifiche", "notifications", .notifications, .notificationsSelected),
            (profile, "Profilo", "profile", .avatar, .profileSelected),
        ]
        for item in items {
            item.controller.tabBarItem = UITabBarItem(title: item.title,
                image: item.icon.image(pointSize: 24), selectedImage: item.selectedIcon.image(pointSize: 24))
            item.controller.tabBarItem.accessibilityIdentifier = "navigation-\(item.id)"
        }
        viewControllers = items.map { $0.controller }
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
        notifications.tabBarItem.accessibilityValue = count > 0 ? "\(count) nuove" : nil
        view.setNeedsLayout()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        guard let viewControllers, let notificationsIndex = viewControllers.firstIndex(where: { $0 === notifications }) else { return }
        let size = notificationsBadge.sizeThatFits(tabBar.bounds.size)
        let available = tabBar.bounds.width - tabBar.safeAreaInsets.left - tabBar.safeAreaInsets.right
        let itemWidth = available / CGFloat(viewControllers.count)
        let center = tabBar.safeAreaInsets.left + itemWidth * (CGFloat(notificationsIndex) + 0.5)
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
