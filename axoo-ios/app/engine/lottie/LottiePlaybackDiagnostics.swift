import Foundation

@MainActor
final class LottiePlaybackDiagnostics {
    static let shared = LottiePlaybackDiagnostics()
    private(set) var publishedFrames = 0
    private(set) var failures = 0
    private(set) var stalledPlayers = 0
    func published() { publishedFrames += 1 }
    func failed() { failures += 1 }
    func stalled() { stalledPlayers += 1 }
    func recovered() { stalledPlayers = max(0, stalledPlayers - 1) }
}

@MainActor
final class LottiePlaybackStallTracker {
    private var lastProgress: TimeInterval?
    private var stalled = false
    func published(at time: TimeInterval) { reset(); lastProgress = time }
    func tick(at time: TimeInterval) {
        guard let lastProgress else { self.lastProgress = time; return }
        if time - lastProgress > 2 && !stalled {
            stalled = true
            LottiePlaybackDiagnostics.shared.stalled()
        }
    }
    func reset() {
        if stalled { LottiePlaybackDiagnostics.shared.recovered() }
        stalled = false
        lastProgress = nil
    }
}
