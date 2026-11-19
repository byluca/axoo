import Foundation

/// Samples source frames without changing the original loop duration.
struct PlaybackTimeline {
    static func frame(at time: TimeInterval, since start: TimeInterval, fps: Double, count: Int,
                      maximumFPS: Double = .infinity) -> Int {
        guard count > 0, fps.isFinite, fps > 0, time.isFinite, start.isFinite else { return 0 }
        let elapsed = max(0, time - start)
        let sampled = maximumFPS.isFinite && maximumFPS > 0 && maximumFPS < fps
            ? floor(elapsed * maximumFPS) / maximumFPS : elapsed
        let frame = floor(sampled * fps)
        guard frame.isFinite else { return 0 }
        return Int(frame.truncatingRemainder(dividingBy: Double(count)))
    }
}

/// Emoji follow an asset's shared phase; decorations retain their local position on pause.
struct LottiePlaybackPosition {
    enum Mode {
        case local
        case synchronized(start: TimeInterval)
    }
    let mode: Mode
    private var startedAt: TimeInterval?
    private var elapsed: TimeInterval = 0

    init(mode: Mode) { self.mode = mode }

    mutating func play(at time: TimeInterval) {
        if startedAt == nil { startedAt = time }
    }
    mutating func pause(at time: TimeInterval) {
        if let start = startedAt { elapsed += max(0, time - start) }
        startedAt = nil
    }
    func position(at time: TimeInterval) -> TimeInterval {
        switch mode {
        case .local: return elapsed + (startedAt.map { max(0, time - $0) } ?? 0)
        case .synchronized(let start): return max(0, time - start)
        }
    }
}
