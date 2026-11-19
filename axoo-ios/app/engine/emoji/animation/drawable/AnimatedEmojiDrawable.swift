import UIKit

/// Shares native animation and prepared frames. Paused hosts defer loading until playback starts.
@MainActor
final class AnimatedEmojiDrawable {
    enum Playback { case still, paused, playing }
    private final class Host {
        weak var value: (any LottieHost)?
        var playback: Playback = .paused
        init(_ value: any LottieHost) { self.value = value }
    }
    let key: AnimatedEmojiKey
    private var hosts: [ObjectIdentifier: Host] = [:]
    private(set) var stillImage: UIImage?
    private var renderedImage: UIImage?
    private var loadTask: Task<Void, Never>?
    private var player: LottiePlayer?
    private var failed = false
    private var disposed = false
    var currentImage: UIImage? { renderedImage ?? stillImage }
    var hasDrawableContent: Bool { currentImage != nil }
    var isAnimationLoaded: Bool { player != nil }
    var isLoadingAnimation: Bool { loadTask != nil }
    var frameBytes: Int { player?.currentFrame.map { $0.image.bytesPerRow * $0.image.height } ?? 0 }

    init(key: AnimatedEmojiKey) { self.key = key }
    func attachTo(_ host: any LottieHost) {
        precondition(!disposed)
        hosts[ObjectIdentifier(host)] = Host(host)
        refreshHostAttachment()
    }
    func detachFrom(_ host: any LottieHost) {
        hosts.removeValue(forKey: ObjectIdentifier(host))
        refreshHostAttachment()
    }
    func requestPlayback(_ host: any LottieHost, mode: Playback) {
        guard let entry = hosts[ObjectIdentifier(host)], entry.playback != mode else { return }
        entry.playback = mode
        refreshHostAttachment()
    }
    func refreshHostAttachment() {
        guard !disposed else { return }
        hosts = hosts.filter { $0.value.value != nil }
        let attached = hosts.values.filter { $0.value?.isAttachedForLottie == true }
        guard !attached.isEmpty else {
            loadTask?.cancel()
            player?.suspend()
            return
        }
        let plays = attached.contains { $0.playback == .playing }
        let needsStill = attached.contains { $0.playback == .still } && stillImage == nil
        if let player {
            if plays && !failed { player.play() }
            else {
                player.pause()
                if needsStill && !failed { player.renderStillFrame() }
            }
        } else if plays || needsStill { loadAnimationIfNeeded() }
        else { loadTask?.cancel() }
    }
    private func loadAnimationIfNeeded() {
        guard !failed, loadTask == nil else { return }
        let path = key.path
        let worker = LottieRenderPool.shared.worker()
        loadTask = Task { [weak self] in
            do {
                let animation = try await worker.load(url: path)
                try Task.checkCancellation()
                guard let self, !self.disposed else { return }
                let start = AnimatedEmojiPlaybackClock.shared.startTime(for: self.key.playbackKey, at: CACurrentMediaTime())
                self.player = LottiePlayer(animation: animation, sizePx: self.key.sizePx,
                    maximumFrameRate: PlaybackPolicy.maximumFrameRate(sizePx: self.key.sizePx, scale: UIScreen.main.scale),
                    timeline: .synchronized(start: start),
                    onFrameReady: { [weak self] frame in
                        guard let self, !self.disposed else { return }
                        self.renderedImage = UIImage(cgImage: frame.image)
                        let suppliesPoster = self.stillImage == nil
                        if suppliesPoster { self.stillImage = self.renderedImage }
                        self.notifyHosts(frameChanged: !suppliesPoster)
                    }, onFailure: { [weak self] _ in self?.failed = true })
            } catch {
                if !(error is CancellationError), let self, !self.disposed {
                    self.failed = true
                    LottiePlaybackDiagnostics.shared.failed()
                }
            }
            guard let self, !self.disposed else { return }
            self.loadTask = nil
            self.refreshHostAttachment()
        }
    }
    private func notifyHosts(frameChanged: Bool) {
        for entry in Array(hosts.values) {
            guard let host = entry.value, host.isAttachedForLottie,
                  !frameChanged || entry.playback == .playing else { continue }
            host.invalidateLottie()
        }
    }
    func dispose() {
        guard !disposed else { return }
        disposed = true
        hosts.removeAll()
        loadTask?.cancel()
        player?.dispose()
        player = nil
        stillImage = nil
        renderedImage = nil
    }
    deinit { loadTask?.cancel() }
}
