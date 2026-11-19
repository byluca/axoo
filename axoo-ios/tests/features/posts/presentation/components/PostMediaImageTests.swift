import XCTest
import UIKit
import ImageIO
@testable import Axoo

@MainActor
final class PostMediaImageTests: XCTestCase {
    func testLoadingPreviewDecodesOnlyOneSmallFrame() throws {
        let data = try makeGIF()
        let still = try XCTUnwrap(PostMediaImage.decode(data, animated: false, maximumPixelSize: 32))
        XCTAssertNil(still.images)
        XCTAssertEqual(still.cgImage?.width, 32)
        XCTAssertEqual(still.cgImage?.height, 32)
        let animated = try XCTUnwrap(PostMediaImage.decode(data, maximumPixelSize: 240))
        XCTAssertEqual(animated.images?.count, 2)
        XCTAssertEqual(animated.duration, 0.2, accuracy: 0.01)
    }

    func testDecodedCacheSeparatesStaticAnimatedAndPixelSizes() async throws {
        MediaImageURLProtocol.data = try makeGIF()
        MediaImageURLProtocol.requests = 0
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MediaImageURLProtocol.self]
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        let url = URL(string: "https://media-image.test/\(UUID().uuidString)")!
        let first = await PostMediaImage.load(url, animated: false, maximumPixelSize: 32, session: session)
        let still = try XCTUnwrap(first)
        let cached = await PostMediaImage.load(url, animated: false, maximumPixelSize: 32, session: session)
        XCTAssertTrue(still === cached)
        XCTAssertEqual(MediaImageURLProtocol.requests, 1)
        let animated = await PostMediaImage.load(url, maximumPixelSize: 32, session: session)
        XCTAssertEqual(animated?.images?.count, 2)
        let larger = await PostMediaImage.load(url, animated: false, maximumPixelSize: 64, session: session)
        XCTAssertEqual(larger?.cgImage?.width, 64)
        XCTAssertEqual(MediaImageURLProtocol.requests, 3)
    }

    func testNetworkFailureIsNotCachedAndCanBeRetried() async throws {
        MediaImageURLProtocol.data = try makeGIF()
        MediaImageURLProtocol.requests = 0
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MediaImageURLProtocol.self]
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        let url = URL(string: "https://media-image.test/retry/\(UUID().uuidString)")!
        let failed = await PostMediaImage.load(url, session: session)
        XCTAssertNil(failed)
        let retried = await PostMediaImage.load(url, session: session)
        XCTAssertNotNil(retried)
        XCTAssertEqual(MediaImageURLProtocol.requests, 2)
    }

    private func makeGIF() throws -> Data {
        let data = NSMutableData()
        let destination = try XCTUnwrap(CGImageDestinationCreateWithData(data as CFMutableData, "com.compuserve.gif" as CFString, 2, nil))
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 64, height: 64), format: {
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            return format
        }())
        for color in [UIColor.red, .blue] {
            let image = renderer.image { context in
                color.setFill()
                context.fill(CGRect(x: 0, y: 0, width: 64, height: 64))
            }
            CGImageDestinationAddImage(destination, try XCTUnwrap(image.cgImage), [
                kCGImagePropertyGIFDictionary: [kCGImagePropertyGIFDelayTime: 0.1]
            ] as CFDictionary)
        }
        XCTAssertTrue(CGImageDestinationFinalize(destination))
        return data as Data
    }
}

private final class MediaImageURLProtocol: URLProtocol {
    static var data = Data()
    private static let lock = NSLock()
    private static var count = 0
    static var requests: Int {
        get { lock.lock(); defer { lock.unlock() }; return count }
        set { lock.lock(); defer { lock.unlock() }; count = newValue }
    }
    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "media-image.test" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        Self.lock.lock()
        Self.count += 1
        let attempt = Self.count
        Self.lock.unlock()
        let status = request.url?.path.hasPrefix("/retry/") == true && attempt == 1 ? 503 : 200
        let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil,
                                       headerFields: ["Content-Type": "image/gif"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Self.data)
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() { }
}
