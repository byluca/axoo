import UIKit
import WebKit
import ImageIO

/// Small shared cache; coalesces requests when the same author appears in several views.
@MainActor
final class AvatarImageLoader {
    static let shared = AvatarImageLoader()
    private let session: URLSession
    private let cache = NSCache<NSURL, UIImage>()
    private var pending: [URL: Task<UIImage, Error>] = [:]
    private var nextRequest = Date.distantPast

    init(session: URLSession = .shared) {
        self.session = session
        cache.totalCostLimit = 8 * 1024 * 1024
    }
    func image(for url: URL) async throws -> UIImage {
        if let image = cache.object(forKey: url as NSURL) { return image }
        if let request = pending[url] { return try await request.value }
        // Raster API requests have a lower rate limit than SVG. Space cache misses.
        let start = max(Date(), nextRequest)
        nextRequest = start.addingTimeInterval(0.15)
        let request = Task { () throws -> UIImage in
            let delay = start.timeIntervalSinceNow
            if delay > 0 { try await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000)) }
            var request = URLRequest(url: url)
            request.timeoutInterval = 15
            let (data, response) = try await session.data(for: request)
            guard let response = response as? HTTPURLResponse, response.statusCode == 200,
                  data.count <= 1_048_576, ["image/png", "image/jpeg"].contains(response.mimeType ?? ""),
                  let source = CGImageSourceCreateWithData(data as CFData, nil),
                  let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
                  let width = properties[kCGImagePropertyPixelWidth] as? Int, width <= 2048,
                  let height = properties[kCGImagePropertyPixelHeight] as? Int, height <= 2048,
                  let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                    kCGImageSourceCreateThumbnailFromImageAlways: true,
                    kCGImageSourceCreateThumbnailWithTransform: true,
                    kCGImageSourceThumbnailMaxPixelSize: 512
                  ] as CFDictionary) else {
                throw URLError(.cannotDecodeContentData)
            }
            return UIImage(cgImage: thumbnail)
        }
        pending[url] = request
        defer { pending[url] = nil }
        let image = try await request.value
        cache.setObject(image, forKey: url as NSURL, cost: Int(image.size.width * image.size.height) * 4)
        return image
    }
}

/// Circular drawing, with no remote work during measurement or fixture previews.
final class AnonAvatarView: AxooDrawingView {
    private var identity: AvatarIdentity?
    private var image: UIImage?
    private var task: Task<Void, Never>?
    private var generation = UUID()
    private var vectorView: WKWebView?
    private var requestedVectorURL: URL?
    private var preloadsOffscreen = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
    }
    /// WebKit renders SVG at the display's resolution for the two large previews.
    convenience init(vector: Bool, preloadsOffscreen: Bool = false) {
        self.init(frame: .zero)
        self.preloadsOffscreen = preloadsOffscreen
        if vector {
            let configuration = WKWebViewConfiguration()
            configuration.defaultWebpagePreferences.allowsContentJavaScript = false
            let web = WKWebView(frame: .zero, configuration: configuration)
            web.isOpaque = false
            web.backgroundColor = .clear
            web.scrollView.backgroundColor = .clear
            web.scrollView.isScrollEnabled = false
            web.isUserInteractionEnabled = false
            vectorView = web
            addSubview(web)
        }
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
    func configure(_ identity: AvatarIdentity?) {
        guard self.identity != identity else { return }
        stop()
        vectorView?.stopLoading()
        requestedVectorURL = nil
        self.identity = identity
        image = nil
        vectorView?.isHidden = true
        setNeedsDisplay()
        loadIfNeeded()
    }
    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil && !preloadsOffscreen { stop() } else { loadIfNeeded() }
    }
    private func stop() {
        generation = UUID()
        task?.cancel()
        task = nil
    }
    func loadIfNeeded() {
        guard window != nil || preloadsOffscreen, image == nil, task == nil else { return }
        // Measurement cards belong to the window but are hidden ancestors.
        var ancestor: UIView? = self
        while !preloadsOffscreen, let view = ancestor {
            guard !view.isHidden, view.alpha > 0 else { return }
            ancestor = view.superview
        }
        if let web = vectorView, identity?.configuration?.isVerified != true {
            guard let url = identity?.svgURL, requestedVectorURL != url else { return }
            let token = generation
            task = Task { [weak self] in
                defer {
                    if let self, self.generation == token { self.task = nil }
                }
                var request = URLRequest(url: url)
                request.timeoutInterval = 15
                for attempt in 0..<2 {
                    do {
                        let (data, response) = try await URLSession.shared.data(for: request)
                        guard !Task.isCancelled, let self, self.generation == token else { return }
                        guard let response = response as? HTTPURLResponse, response.statusCode == 200,
                              response.mimeType == "image/svg+xml", data.count <= 1_048_576,
                              String(data: data, encoding: .utf8) != nil else { return }
                        let source = "data:image/svg+xml;base64," + data.base64EncodedString()
                        web.loadHTMLString("""
                            <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
                            <style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:transparent;color-scheme:light}
                            img{display:block;width:100%;height:100%;object-fit:contain}</style></head>
                            <body><img src="\(source)" alt=""></body></html>
                            """, baseURL: nil)
                        self.requestedVectorURL = url
                        web.isHidden = false
                        return
                    } catch {
                        // Un solo nuovo tentativo per gli errori temporanei di connessione.
                        guard attempt == 0, !Task.isCancelled, let error = error as? URLError,
                              [URLError.Code.timedOut, .networkConnectionLost, .notConnectedToInternet,
                               .cannotConnectToHost, .cannotFindHost, .dnsLookupFailed].contains(error.code) else { return }
                        do { try await Task.sleep(nanoseconds: 1_000_000_000) }
                        catch { return }
                    }
                }
            }
            return
        }
        guard let url = identity?.imageURL else { return }
        let token = generation
        task = Task { [weak self] in
            let image = try? await AvatarImageLoader.shared.image(for: url)
            guard !Task.isCancelled, let self = self, self.generation == token else { return }
            self.task = nil
            self.image = image
            self.setNeedsDisplay()
        }
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        vectorView?.frame = bounds
        vectorView?.layer.cornerRadius = min(bounds.width, bounds.height) / 2
        vectorView?.clipsToBounds = true
    }
    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        let diameter = min(bounds.width, bounds.height)
        let circle = CGRect(x: bounds.midX - diameter / 2, y: bounds.midY - diameter / 2, width: diameter, height: diameter)
        context.saveGState()
        context.addEllipse(in: circle)
        context.clip()
        AxooTheme.surface.setFill()
        context.fill(circle)
        if let image = image {
            image.draw(in: circle)
        } else {
            AxooIcon.avatar.image?.withTintColor(.secondaryLabel, renderingMode: .alwaysOriginal)
                .draw(in: circle.insetBy(dx: diameter * 0.22, dy: diameter * 0.22))
        }
        context.restoreGState()
    }
}
