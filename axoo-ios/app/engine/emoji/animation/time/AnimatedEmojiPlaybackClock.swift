import Foundation

/// Late joiners use the same phase for an asset, regardless of raster size.
@MainActor
final class AnimatedEmojiPlaybackClock {
    static let shared = AnimatedEmojiPlaybackClock()
    private var starts: [String: TimeInterval] = [:]
    private var startOrder: [String] = []

    func startTime(for id: Int, at time: TimeInterval) -> TimeInterval {
        startTime(for: String(id), at: time)
    }
    func startTime(for id: String, at time: TimeInterval) -> TimeInterval {
        if let existing = starts[id] { return existing }
        starts[id] = time
        startOrder.append(id)
        if startOrder.count > 512 { starts.removeValue(forKey: startOrder.removeFirst()) }
        return time
    }
}
