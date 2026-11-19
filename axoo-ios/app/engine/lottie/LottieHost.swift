import UIKit

/// A surface that displays already rendered content. Native work never runs here.
@MainActor
protocol LottieHost: AnyObject {
    var isAttachedForLottie: Bool { get }
    func invalidateLottie()
}
