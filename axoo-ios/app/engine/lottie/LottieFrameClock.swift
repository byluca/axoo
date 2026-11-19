import UIKit

/// Native VSync adapter. PlaybackTimeline decides which source frames to skip.
@MainActor
final class LottieFrameClock {
    @MainActor private final class Target: NSObject {
        weak var owner: LottieFrameClock?
        @objc func tick(_ link: CADisplayLink) { owner?.tick(link.timestamp) }
    }
    static let shared = LottieFrameClock()
    private var listeners: [UUID: (TimeInterval) -> Void] = [:]
    private var updates: [UUID: () -> Void] = [:]
    private let target = Target()
    private var displayLink: CADisplayLink?
    private var deliveringFrame = false
    private let automaticallyTicks: Bool

    init(automaticallyTicks: Bool = true) {
        self.automaticallyTicks = automaticallyTicks
        target.owner = self
    }
    // One-shot tasks (like layout changes during fast scroll or rotation). Executed once on the next tick and then deleted, preventing lag.
    func scheduleUpdate(id: UUID, _ update: @escaping () -> Void) {
        updates[id] = update
        start()
    }
    func cancelUpdate(id: UUID) {
        updates.removeValue(forKey: id)
        stopIfIdle()
    }
    func addListener(id: UUID, callback: @escaping (TimeInterval) -> Void) {
        listeners[id] = callback
        start()
    }
    func removeListener(id: UUID) {
        listeners.removeValue(forKey: id)
        stopIfIdle()
    }
    func tick(_ time: TimeInterval) {
        deliveringFrame = true
        defer { deliveringFrame = false; stopIfIdle() }
        let pending = Array(updates.values)
        updates.removeAll(keepingCapacity: true)
        for update in pending { update() }
        for callback in Array(listeners.values) { callback(time) }
    }
    private func start() {
        guard automaticallyTicks, displayLink == nil else { return }
        let link = CADisplayLink(target: target, selector: #selector(Target.tick(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 60, preferred: 60)
        link.add(to: .main, forMode: .common)
        displayLink = link
    }
    private func stopIfIdle() {
        guard !deliveringFrame, listeners.isEmpty, updates.isEmpty else { return }
        displayLink?.invalidate(); displayLink = nil
    }
    deinit { displayLink?.invalidate() }
}
