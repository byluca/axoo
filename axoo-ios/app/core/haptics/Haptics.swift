import UIKit

@MainActor
final class Haptics {
    private static let shared = Haptics()
    private lazy var impact = UIImpactFeedbackGenerator(style: .medium)
    private lazy var confirmation = UINotificationFeedbackGenerator()

    private init() {}

    static func prepare() {
        shared.impact.prepare()
    }

    static func vibrate() {
        let generator = shared.impact
        generator.impactOccurred()
        generator.prepare()
    }

    static func commentSent() {
        shared.confirmation.notificationOccurred(.success)
        shared.confirmation.prepare()
    }
}
