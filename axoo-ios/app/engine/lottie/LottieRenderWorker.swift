import Foundation

/// Serial native executor owned by the shared pool. No UIKit work runs here.
actor LottieRenderWorker {
    func load(url: URL) throws -> RLottieAnimation {
        try Task.checkCancellation()
        let animation = try RLottieAnimation(data: Data(contentsOf: url, options: .mappedIfSafe))
        try Task.checkCancellation()
        return animation
    }
    func render(_ animation: RLottieAnimation, frame: Int, edge: Int) throws -> LottieFrame {
        try Task.checkCancellation()
        let result = try AnimationFrameRenderer.render(animation, frame: frame, edge: edge)
        try Task.checkCancellation()
        return result
    }
}
