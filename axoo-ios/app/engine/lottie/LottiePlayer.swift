import Foundation
import QuartzCore

/// One native render in flight, retaining only the latest requested frame and size.
/// Owns VSync registration for both synchronized emoji and local decorative playback.
@MainActor
final class LottiePlayer {
    private struct Request: Equatable { let index: Int; let edge: Int }
    let animation: RLottieAnimation
    private var edge: Int
    private let maximumFrameRate: Double
    private var position: LottiePlaybackPosition
    private let clock: LottieFrameClock
    private let clockID = UUID()
    private let onFrameReady: (LottieFrame) -> Void
    private let onFailure: (Error) -> Void
    private var renderTask: Task<Void, Never>?
    private var requested: Request?
    private var published: Request?
    private(set) var currentFrame: LottieFrame?
    private(set) var playing = false
    private var disposed = false
    private let stallTracker = LottiePlaybackStallTracker()

    init(animation: RLottieAnimation, sizePx: Int, maximumFrameRate: Double = 60,
         timeline: LottiePlaybackPosition.Mode = .local, clock: LottieFrameClock? = nil,
         onFrameReady: @escaping (LottieFrame) -> Void, onFailure: @escaping (Error) -> Void) {
        precondition((1...1024).contains(sizePx))
        self.animation = animation
        edge = sizePx
        self.maximumFrameRate = maximumFrameRate
        position = LottiePlaybackPosition(mode: timeline)
        self.clock = clock ?? .shared
        self.onFrameReady = onFrameReady
        self.onFailure = onFailure
    }
    func resize(to sizePx: Int) {
        precondition((1...1024).contains(sizePx))
        edge = sizePx
    }
    func play(at time: TimeInterval = CACurrentMediaTime()) {
        guard !disposed, !playing else { return }
        playing = true
        position.play(at: time)
        clock.addListener(id: clockID) { [weak self] in self?.onVsync($0) }
        onVsync(time)
    }
    func pause(at time: TimeInterval = CACurrentMediaTime()) {
        guard playing else { return }
        playing = false
        position.pause(at: time)
        clock.removeListener(id: clockID)
        requested = nil
        renderTask?.cancel()
        stallTracker.reset()
    }
    /// Also cancels a queued still frame when its surface becomes invisible.
    func suspend() {
        pause()
        requested = nil
        renderTask?.cancel()
    }
    func renderCurrentFrame(at time: TimeInterval = CACurrentMediaTime()) { requestFrame(frame(at: time)) }
    func renderStillFrame(_ index: Int = 0) { requestFrame(index) }
    func onVsync(_ time: TimeInterval) {
        guard playing, !disposed else { return }
        requestFrame(frame(at: time))
        if published != requested { stallTracker.tick(at: time) }
        else { stallTracker.published(at: time) }
    }
    private func frame(at time: TimeInterval) -> Int {
        PlaybackTimeline.frame(at: position.position(at: time), since: 0, fps: animation.framesPerSecond,
                               count: animation.frameCount, maximumFPS: maximumFrameRate)
    }
    private func requestFrame(_ index: Int) {
        guard !disposed else { return }
        requested = Request(index: index, edge: edge)
        renderLatestIfNeeded()
    }
    private func renderLatestIfNeeded() {
        guard !disposed, renderTask == nil, let request = requested, request != published else { return }
        let worker = LottieRenderPool.shared.worker()
        let animation = self.animation
        renderTask = Task { [weak self] in
            do {
                let result = try await worker.render(animation, frame: request.index, edge: request.edge)
                try Task.checkCancellation()
                guard let self, !self.disposed else { return }
                self.renderTask = nil
                // Do not publish an old raster size or update a paused/disposed host.
                if self.requested == request || (self.playing && self.requested?.edge == request.edge) {
                    self.currentFrame = result
                    self.published = request
                    self.stallTracker.published(at: CACurrentMediaTime())
                    LottiePlaybackDiagnostics.shared.published()
                    self.onFrameReady(result)
                }
                self.renderLatestIfNeeded()
            } catch {
                guard let self else { return }
                self.renderTask = nil
                guard !self.disposed else { return }
                if error is CancellationError { self.renderLatestIfNeeded() }
                else {
                    self.suspend()
                    LottiePlaybackDiagnostics.shared.failed()
                    self.onFailure(error)
                }
            }
        }
    }
    func dispose() {
        guard !disposed else { return }
        disposed = true
        suspend()
        currentFrame = nil
    }
    deinit {
        renderTask?.cancel()
        let clock = clock, id = clockID
        Task { @MainActor in clock.removeListener(id: id) }
    }
}
