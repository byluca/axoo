import UIKit

/// UI surface only: owns a lease, displays prepared images and reports attachment.
@MainActor
final class AnimatedLottieView: UIView, LottieHost {
    let leaseID = UUID()
    var textPlaybackGroup: UUID?
    private(set) var asset: Axooji?
    var playbackEnabled = true { didSet { if oldValue != playbackEnabled { refreshVisibility() } } }
    var animates = true { didSet { if oldValue != animates { refreshVisibility() } } }
    var showsUnicodeFallback = true
    var pickerRaster = false
    var onContentReady: (() -> Void)?
    private let fallback = UILabel()
    private let imageView = UIImageView()
    private var lease: AnimatedEmojiLease?
    private var lastImage: UIImage?
    private var contentReady = false
    private var lastLayoutFrame = CGRect.null
    private var lastLayoutBounds = CGRect.null
    private var playback: AnimatedEmojiDrawable.Playback = .still
    private(set) var isAttachedForLottie = false
    override var isHidden: Bool { didSet { refreshVisibility() } }
    var hasDrawableContent: Bool { lease?.drawable.hasDrawableContent == true }
    var renderKey: AnimatedEmojiKey? {
        guard let asset, let path = AxoojiCatalog.shared.animationURL(for: asset), bounds.width > 0, bounds.height > 0 else { return nil }
        let scale = window?.screen.scale ?? UIScreen.main.scale
        let size: Int
        if pickerRaster {
            size = EmojiRasterSizePolicy.pickerSizePx(scale: scale)
        } else {
            size = EmojiRasterSizePolicy.rasterSizePx(renderSizePoints: max(bounds.width, bounds.height), scale: scale)
        }
        return AnimatedEmojiKey(id: asset.id, path: path, sizePx: size)
    }
    var maximumFPS: Double {
        let scale = window?.screen.scale ?? UIScreen.main.scale
        let size = renderKey?.sizePx ?? EmojiRasterSizePolicy.rasterSizePx(renderSizePoints: max(bounds.width, bounds.height), scale: scale)
        return PlaybackPolicy.maximumFrameRate(sizePx: size, scale: scale)
    }
    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        fallback.textAlignment = .center
        fallback.adjustsFontSizeToFitWidth = true
        fallback.minimumScaleFactor = 0.2
        imageView.contentMode = .scaleAspectFit
        addSubview(fallback); addSubview(imageView)
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    deinit {
        let lease = lease
        Task { @MainActor in lease?.close() }
    }
    func configure(_ emoji: Axooji?) {
        guard asset != emoji else { return }
        lease?.close(); lease = nil
        asset = emoji
        contentReady = false
        lastImage = nil
        imageView.image = nil
        fallback.text = emoji?.fallback
        fallback.isHidden = !showsUnicodeFallback
        refreshVisibility()
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        guard lastLayoutFrame != frame || lastLayoutBounds != bounds else { return }
        lastLayoutFrame = frame
        lastLayoutBounds = bounds
        fallback.frame = bounds; imageView.frame = bounds
        fallback.font = .systemFont(ofSize: max(12, min(bounds.width, bounds.height) * 0.7))
        refreshVisibility()
    }
    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil {
            EmojiPlaybackCoordinator.shared.detach(self)
            lease?.close(); lease = nil
        } else { EmojiPlaybackCoordinator.shared.attach(self) }
    }
    private func refreshVisibility() {
        if window != nil { EmojiPlaybackCoordinator.shared.setNeedsVisibilityUpdate() }
    }
    func updatePlayback(visible: Bool, key: AnimatedEmojiKey?, allowed: Bool) {
        isAttachedForLottie = visible
        guard visible, let key else {
            lease?.close(); lease = nil
            return
        }
        playback = animates ? (allowed ? .playing : .paused) : .still
        if lease?.key != key {
            contentReady = false
            lastImage = nil
            imageView.image = nil
            fallback.isHidden = !showsUnicodeFallback
            lease?.close()
            lease = AnimatedEmojiRegistry.shared.acquire(key: key, host: self)
        }
        lease?.setPlayback(playback)
        invalidateLottie()
    }
    func invalidateLottie() {
        guard let lease else { return }
        let image: UIImage?
        switch playback {
        case .still: image = lease.drawable.stillImage
        case .paused: image = lastImage ?? lease.drawable.stillImage
        case .playing: image = lease.drawable.currentImage
        }
        if let image, lastImage !== image {
            lastImage = image
            imageView.image = image
            fallback.isHidden = true
        }
        if hasDrawableContent && !contentReady {
            contentReady = true
            onContentReady?()
        }
    }
}
