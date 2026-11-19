import UIKit
import UserNotifications

@main
@MainActor
final class AxooApp: UIResponder, UIApplicationDelegate {
    let push = PushNotifications()

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        push.configure()
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        push.registeredAPNs(deviceToken)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        NSLog("Push: registrazione APNs non riuscita")
    }

    func application(_ application: UIApplication,
                     configurationForConnecting session: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: "Axoo", sessionRole: session.role)
        configuration.delegateClass = SceneDelegate.self
        return configuration
    }
}

@MainActor
final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession,
               options connectionOptions: UIScene.ConnectionOptions) {
        guard let scene = scene as? UIWindowScene else { return }
        
        let backIcon = AxooIcon.back.image(pointSize: 24)
        UINavigationBar.appearance().backIndicatorImage = backIcon
        UINavigationBar.appearance().backIndicatorTransitionMaskImage = backIcon
        
        let window = UIWindow(windowScene: scene)
        window.overrideUserInterfaceStyle = AxooTheme.interfaceStyle
        window.backgroundColor = AxooTheme.background
        window.tintColor = AxooTheme.accent
        window.rootViewController = makeRootController()
        self.window = window
        window.makeKeyAndVisible()
        if let response = connectionOptions.notificationResponse {
            (UIApplication.shared.delegate as? AxooApp)?.push.open(userInfo: response.notification.request.content.userInfo)
        }
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        Haptics.prepare()
    }

    func sceneDidDisconnect(_ scene: UIScene) {
        (window?.rootViewController as? AuthRootView)?.stop()
    }

    func sceneWillEnterForeground(_ scene: UIScene) {
        (window?.rootViewController as? AuthRootView)?.resume()
    }

    private func makeRootController() -> UIViewController {
        let service = AppDependencies.authService()
        let operations = AuthOperations(service: service)
        let push = (UIApplication.shared.delegate as? AxooApp)?.push
        if let repository = service as? AuthRepository {
            push?.connect(api: repository)
            repository.onSessionCleared = { [weak push] token in push?.signedOut(accessToken: token) }
        }
        let root = AuthRootView(operations: operations, api: service as? APIClient, push: push)
        push?.onOpen = { [weak root] in root?.openPendingPush() }
        (service as? AuthRepository)?.onAccessChanged = { [weak root] in root?.viewModel.restore() }
        return root
    }
}
