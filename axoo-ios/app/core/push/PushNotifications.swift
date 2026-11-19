import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging
import FirebaseInstallations

@MainActor
final class PushNotifications: NSObject, MessagingDelegate, UNUserNotificationCenterDelegate {
    private let center = UNUserNotificationCenter.current()
    private var activation: Task<Void, Never>?
    private(set) var registration: PushRegistration?
    private(set) var isConfigured = false
    private(set) var pendingTarget: PushTarget?
    var onOpen: () -> Void = {}

    func configure() {
        guard let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: path),
              options.bundleID == Bundle.main.bundleIdentifier else { return }
        FirebaseApp.configure(options: options)
        Messaging.messaging().delegate = self
        center.delegate = self
        isConfigured = true
    }

    func connect(api: APIClient, configuration: AppConfiguration = .current) {
        guard isConfigured else { return }
        let http = HTTPClient()
        registration = PushRegistration(api: api, remove: { id, registration, token in
            _ = try await http.request("DELETE", url: configuration.apiURL.appendingPathComponent("users/me/push-installations/\(id)"),
                json: APIJSON.body(["registration": registration]), headers: ["Authorization": "Bearer \(token)"], timeout: 3)
        }, register: {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                Messaging.messaging().register { error in
                    if let error { continuation.resume(throwing: error) } else { continuation.resume() }
                }
            }
            return try await Installations.installations().installationID()
        }, invalidate: {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                Messaging.messaging().unregister { error in
                    if let error { continuation.resume(throwing: error) } else { continuation.resume() }
                }
            }
        })
    }

    func activate(userID: String) {
        guard let registration else { return }
        registration.activate(userID: userID)
        activation?.cancel()
        activation = Task {
            let settings = await center.notificationSettings()
            guard !Task.isCancelled, registration.userID == userID,
                  [.authorized, .provisional, .ephemeral].contains(settings.authorizationStatus),
                  await registration.prepare(), !Task.isCancelled else { return }
            UIApplication.shared.registerForRemoteNotifications()
        }
    }

    func registeredAPNs(_ token: Data) {
        guard isConfigured, registration?.userID != nil else { return }
        Messaging.messaging().apnsToken = token
        registration?.synchronize()
    }

    func stop() {
        activation?.cancel()
        registration?.stop()
        center.removeAllDeliveredNotifications()
    }

    func signedOut(accessToken: String?) {
        stop()
        clearPendingTarget()
        UIApplication.shared.unregisterForRemoteNotifications()
        registration?.signedOut(accessToken: accessToken)
    }

    func clearSignedOut() {
        if registration?.hasSavedRegistration == true { signedOut(accessToken: nil) }
    }

    func shouldShowPermission() async -> Bool {
        guard isConfigured else { return false }
        let settings = await center.notificationSettings()
        return settings.authorizationStatus == .notDetermined && !UserDefaults.standard.bool(forKey: "push_permission_asked")
    }
    func markPermissionShown() { UserDefaults.standard.set(true, forKey: "push_permission_asked") }
    func requestPermission() {
        Task {
            _ = try? await center.requestAuthorization(options: [.alert, .sound])
            if let userID = registration?.userID { activate(userID: userID) }
        }
    }

    func open(userInfo: [AnyHashable: Any]) {
        guard let target = PushTarget(userInfo: userInfo) else { return }
        pendingTarget = target
        onOpen()
    }
    func clearPendingTarget() { pendingTarget = nil }

    nonisolated func messaging(_ messaging: Messaging, didReceiveRegistration installationId: String?) {
        guard let installationId else { return }
        Task { @MainActor in self.registration?.synchronize(installationId) }
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
        willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        let target = PushTarget(userInfo: notification.request.content.userInfo)
        Task { @MainActor in
            let matches = target != nil && target?.recipientID == self.registration?.userID
            if matches { NotificationCenter.default.post(name: Notification.Name("axooNotificationsChanged"), object: nil) }
            completionHandler(matches ? [.banner, .list, .sound] : [])
        }
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        let target = PushTarget(userInfo: response.notification.request.content.userInfo)
        Task { @MainActor in
            if let target { self.pendingTarget = target; self.onOpen() }
            completionHandler()
        }
    }
}
