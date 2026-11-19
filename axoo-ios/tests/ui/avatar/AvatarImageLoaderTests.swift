import XCTest
import UIKit
@testable import Axoo

@MainActor
final class AvatarImageLoaderTests: XCTestCase {
    func testConcurrentRequestsAndCacheShareOneDownload() async throws {
        let (loader, session) = makeLoader()
        defer { session.invalidateAndCancel() }
        let url = URL(string: "https://avatar.test/success")!
        async let first = loader.image(for: url)
        async let second = loader.image(for: url)
        let (a, b) = try await (first, second)
        let cached = try await loader.image(for: url)
        XCTAssertTrue(a === b)
        XCTAssertTrue(a === cached)
        XCTAssertEqual(AvatarURLProtocol.count, 1)
    }
    func testFailureIsNotCachedAndCanBeRetried() async throws {
        let (loader, session) = makeLoader()
        defer { session.invalidateAndCancel() }
        let url = URL(string: "https://avatar.test/retry")!
        do {
            _ = try await loader.image(for: url)
            XCTFail("HTTP failure must leave the placeholder")
        } catch { }
        let image = try await loader.image(for: url)
        XCTAssertGreaterThan(image.size.width, 0)
        XCTAssertEqual(AvatarURLProtocol.count, 2)
    }
    private func makeLoader() -> (AvatarImageLoader, URLSession) {
        AvatarURLProtocol.reset()
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [AvatarURLProtocol.self]
        let session = URLSession(configuration: configuration)
        return (AvatarImageLoader(session: session), session)
    }
}

private final class AvatarURLProtocol: URLProtocol {
    private static let lock = NSLock()
    private static var requests = 0
    static var count: Int { lock.lock(); defer { lock.unlock() }; return requests }
    static func reset() { lock.lock(); defer { lock.unlock() }; requests = 0 }
    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "avatar.test" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        Self.lock.lock()
        Self.requests += 1
        let attempt = Self.requests
        Self.lock.unlock()
        let status = request.url?.path == "/retry" && attempt == 1 ? 503 : 200
        let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: ["Content-Type": "image/png"])!
        let png = Data(base64Encoded: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==")!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: png)
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() { }
}
