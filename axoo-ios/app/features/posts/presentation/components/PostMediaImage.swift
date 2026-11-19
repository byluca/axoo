import UIKit
import ImageIO

/// ImageIO detects animation from the file contents, including URLs without an extension.
enum PostMediaImage {
    private static let klipySession: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil
        return URLSession(configuration: configuration)
    }()
    private static let cache: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.totalCostLimit = 24 * 1024 * 1024
        cache.countLimit = 64
        return cache
    }()

    static func load(_ url: URL, animated: Bool = true, maximumPixelSize: Int = 600,
                     session: URLSession = .shared) async -> UIImage? {
        guard !Task.isCancelled else { return nil }
        // Static placeholders and animated previews must not share a decoded entry.
        let key = "\(url.absoluteString)|\(animated)|\(maximumPixelSize)" as NSString
        let cacheAllowed = !KlipyMedia.isMediaURL(url)
        if cacheAllowed, let image = cache.object(forKey: key) { return image }
        var request = URLRequest(url: url)
        if !cacheAllowed { request.cachePolicy = .reloadIgnoringLocalCacheData }
        guard let (data, response) = try? await (cacheAllowed ? session : klipySession).data(for: request),
              let response = response as? HTTPURLResponse, (200..<300).contains(response.statusCode),
              !Task.isCancelled else { return nil }
        let decoding = Task.detached(priority: .utility) {
            decode(data, animated: animated, maximumPixelSize: maximumPixelSize)
        }
        let image = await withTaskCancellationHandler(operation: {
            await decoding.value
        }, onCancel: { decoding.cancel() })
        guard !Task.isCancelled, let image else { return nil }
        let frames = image.images ?? [image]
        let cost = frames.reduce(0) { $0 + ($1.cgImage.map { $0.bytesPerRow * $0.height } ?? 0) }
        if cacheAllowed && cost <= cache.totalCostLimit { cache.setObject(image, forKey: key, cost: cost) }
        return image
    }

    static func decode(_ data: Data, animated: Bool = true, maximumPixelSize: Int = 600) -> UIImage? {
        guard !Task.isCancelled else { return nil }
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
        let count = CGImageSourceGetCount(source)
        let pixelLimit = animated && count > 1 ? min(600, maximumPixelSize) : maximumPixelSize
        let options: [CFString: Any] = [kCGImageSourceCreateThumbnailFromImageAlways: true,
                                        kCGImageSourceThumbnailMaxPixelSize: max(1, pixelLimit),
                                        kCGImageSourceCreateThumbnailWithTransform: true]
        guard animated, count > 1 else {
            return CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary).map { UIImage(cgImage: $0) }
        }
        let strideSize = max(1, Int(ceil(Double(count) / 30)))
        var frames: [UIImage] = []
        var duration: TimeInterval = 0
        for index in stride(from: 0, to: count, by: strideSize) {
            guard !Task.isCancelled else { return nil }
            guard let frame = CGImageSourceCreateThumbnailAtIndex(source, index, options as CFDictionary) else { continue }
            frames.append(UIImage(cgImage: frame))
            for frameIndex in index..<min(count, index + strideSize) {
                let properties = CGImageSourceCopyPropertiesAtIndex(source, frameIndex, nil) as? [CFString: Any]
                let gif = properties?[kCGImagePropertyGIFDictionary] as? [CFString: Any]
                let webp = properties?[kCGImagePropertyWebPDictionary] as? [CFString: Any]
                let delay = (webp?[kCGImagePropertyWebPUnclampedDelayTime] as? Double)
                    ?? (webp?[kCGImagePropertyWebPDelayTime] as? Double)
                    ?? (gif?[kCGImagePropertyGIFUnclampedDelayTime] as? Double)
                    ?? (gif?[kCGImagePropertyGIFDelayTime] as? Double) ?? 0.1
                duration += max(0.02, delay)
            }
        }
        guard frames.count > 1 else { return frames.first }
        return UIImage.animatedImage(with: frames, duration: duration)
    }
}

/// Native UIImageView playback, paused when the image leaves the screen.
final class PostMediaImageView: UIImageView {
    private var mediaTask: Task<Void, Never>?
    private var source: String?
    private var previewSource: String?
    private var maximumPixelSize = 600
    private var requestID = UUID()
    private(set) var ready = false
    private(set) var decodedImage: UIImage?
    var onReady: (() -> Void)?
    private let loadingIndicator = UIActivityIndicatorView(style: .medium)
    var isLoading = false {
        didSet { if isLoading { loadingIndicator.startAnimating() } else { loadingIndicator.stopAnimating() } }
    }
    var playbackEnabled = true { didSet { updatePlayback() } }

    override init(frame: CGRect) {
        super.init(frame: frame)
        loadingIndicator.color = .white
        loadingIndicator.hidesWhenStopped = true
        addSubview(loadingIndicator)
        NotificationCenter.default.addObserver(self, selector: #selector(pausePlayback),
            name: UIApplication.willResignActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(updatePlayback),
            name: UIApplication.didBecomeActiveNotification, object: nil)
    }
    convenience init() { self.init(frame: .zero) }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    deinit { NotificationCenter.default.removeObserver(self) }

    func load(_ url: String?, previewURL: String? = nil, maximumPixelSize: Int = 600) {
        guard source != url || previewSource != previewURL || self.maximumPixelSize != maximumPixelSize else { return }
        clear()
        source = url
        previewSource = previewURL
        self.maximumPixelSize = maximumPixelSize
        loadIfVisible()
    }

    func clear() {
        requestID = UUID()
        mediaTask?.cancel(); mediaTask = nil
        source = nil; previewSource = nil
        ready = false; decodedImage = nil
        isLoading = false
        display(nil)
    }

    private func loadIfVisible() {
        guard window != nil, !ready, mediaTask == nil,
              let source, let url = URL(string: source), url.scheme == "https" else { return }
        let request = requestID
        let preview = previewSource
        let pixelSize = maximumPixelSize
        isLoading = true
        mediaTask = Task { [weak self] in
            await withTaskGroup(of: (Bool, UIImage?).self) { group in
                group.addTask { (true, await PostMediaImage.load(url, maximumPixelSize: pixelSize)) }
                if let preview, preview != source, let previewURL = URL(string: preview), previewURL.scheme == "https" {
                    group.addTask { (false, await PostMediaImage.load(previewURL, animated: false, maximumPixelSize: 32)) }
                }
                for await (complete, image) in group {
                    guard !Task.isCancelled, let self, self.requestID == request else { group.cancelAll(); return }
                    if let image {
                        self.display(image)
                        if complete {
                            self.ready = true
                            self.decodedImage = image
                            self.onReady?()
                        }
                    }
                    if complete { group.cancelAll(); return }
                }
            }
            guard !Task.isCancelled, let self, self.requestID == request else { return }
            self.mediaTask = nil
            self.isLoading = false
        }
    }

    func display(_ decoded: UIImage?) {
        stopAnimating()
        animationImages = decoded?.images
        image = decoded?.images?.first ?? decoded
        animationDuration = decoded?.duration ?? 0
        animationRepeatCount = 0
        updatePlayback()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil {
            mediaTask?.cancel(); mediaTask = nil
            isLoading = false
        } else { loadIfVisible() }
        updatePlayback()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        loadingIndicator.center = CGPoint(x: bounds.midX, y: bounds.midY)
        updatePlayback()
    }

    @objc private func pausePlayback() { stopAnimating() }

    @objc func updatePlayback() {
        let shouldPlay = playbackEnabled && animationImages != nil && axooIsVisible
        if shouldPlay && !isAnimating { startAnimating() }
        else if !shouldPlay && isAnimating { stopAnimating() }
    }
}
