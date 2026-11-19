import Foundation

/// Bounded native concurrency, shared by emoji and decorative players.
/// Workers are serial actors, not dedicated OS threads. Swift schedules them.
@MainActor
final class LottieRenderPool {
    static let shared = LottieRenderPool()
    private let workers: [LottieRenderWorker]
    private var nextWorker = 0
    var workerCount: Int { workers.count }

    // Restore the two lanes used before the Android-shaped reorganization.
    // Fix the pool size for its lifetime so reconfiguration cannot leave old lanes running.
    init(workerCount: Int = 2) {
        workers = (0..<min(4, max(1, workerCount))).map { _ in LottieRenderWorker() }
    }
    func worker() -> LottieRenderWorker {
        let worker = workers[nextWorker]
        nextWorker = (nextWorker + 1) % workers.count
        return worker
    }
}
