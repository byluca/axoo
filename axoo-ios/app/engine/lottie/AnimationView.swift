import UIKit

/// Plays bundled TGS illustrations with the same renderer as emoji.
@MainActor
final class AnimationView: UIImageView {
    var playbackEnabled = true { didSet { updateAnimationVisibility() } }
    override var isHidden: Bool { didSet { updateAnimationVisibility() } }
    private var assetURL: URL?
    private var assetVersion = UUID()
    private var loadTask: Task<Void, Never>?
    private var player: LottiePlayer?
    private var failed = false
    private var applicationActive = UIApplication.shared.applicationState == .active

    init(name: String, bundle: Bundle = .main) {
        super.init(frame: .zero)
        contentMode = .scaleAspectFit
        isUserInteractionEnabled = false
        NotificationCenter.default.addObserver(self, selector: #selector(becameActive),
                                               name: UIApplication.didBecomeActiveNotification, object: nil)
        for name in [UIApplication.willResignActiveNotification, UIApplication.didEnterBackgroundNotification] {
            NotificationCenter.default.addObserver(self, selector: #selector(resignedActive), name: name, object: nil)
        }
        configure(name: name, bundle: bundle)
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }

    deinit {
        loadTask?.cancel()
        let player = player
        Task { @MainActor in player?.dispose() }
        NotificationCenter.default.removeObserver(self)
    }
    func configure(name: String, bundle: Bundle = .main) {
        let url = bundle.url(forResource: name, withExtension: "tgs", subdirectory: "axooji/ui")
        guard url != assetURL else { return }
        loadTask?.cancel()
        loadTask = nil
        player?.dispose()
        player = nil
        assetVersion = UUID()
        assetURL = url
        image = nil
        failed = false
        updateAnimationVisibility()
    }
    override func layoutSubviews() { super.layoutSubviews(); updateAnimationVisibility() }
    override func didMoveToWindow() { super.didMoveToWindow(); updateAnimationVisibility() }
    @objc private func becameActive() { applicationActive = true; updateAnimationVisibility() }
    @objc private func resignedActive() { applicationActive = false; updateAnimationVisibility() }

    func updateAnimationVisibility() {
        guard applicationActive, axooIsVisible, bounds.width > 0, bounds.height > 0,
              let url = assetURL, !failed else {
            loadTask?.cancel()
            player?.suspend()
            return
        }
        let scale = window?.screen.scale ?? UIScreen.main.scale
        let edge = Int(min(CGFloat(1024), max(CGFloat(1), ceil(max(bounds.width, bounds.height) * scale))))
        guard let player else { loadIfNeeded(url: url, edge: edge); return }
        player.resize(to: edge)
        if playbackEnabled {
            player.play()
        } else {
            player.pause()
        }
        player.renderCurrentFrame()
    }
    private func loadIfNeeded(url: URL, edge: Int) {
        guard loadTask == nil else { return }
        let version = assetVersion
        let worker = LottieRenderPool.shared.worker()
        loadTask = Task { [weak self] in
            defer {
                if let self, self.assetVersion == version {
                    self.loadTask = nil
                    self.updateAnimationVisibility()
                }
            }
            do {
                let animation = try await worker.load(url: url)
                try Task.checkCancellation()
                guard let self, self.assetVersion == version else { return }
                self.player = LottiePlayer(animation: animation, sizePx: edge,
                    onFrameReady: { [weak self] frame in
                        guard let self, self.assetVersion == version else { return }
                        guard self.applicationActive, self.axooIsVisible else {
                            self.updateAnimationVisibility()
                            return
                        }
                        self.image = UIImage(cgImage: frame.image, scale: self.window?.screen.scale ?? 2, orientation: .up)
                    }, onFailure: { [weak self] _ in
                        guard let self, self.assetVersion == version else { return }
                        self.failed = true
                    })
            } catch is CancellationError { }
            catch {
                guard let self, self.assetVersion == version else { return }
                self.failed = true
                LottiePlaybackDiagnostics.shared.failed()
            }
        }
    }
}
