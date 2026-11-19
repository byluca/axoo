import XCTest
import UIKit
@testable import Axoo

@MainActor
final class RemoteMediaPickerTests: XCTestCase {
    func testAupIsAvailableBeforeRemoteResultsAndSelectionKeepsOriginalWebP() throws {
        let picker = RemoteMediaPickerPanelView(api: nil, kind: .sticker)
        picker.frame = CGRect(x: 0, y: 0, width: 390, height: 600)
        picker.layoutIfNeeded()
        let collection = try XCTUnwrap(picker.subviews.compactMap { $0 as? UICollectionView }.first)
        XCTAssertEqual(collection.numberOfSections, 3)
        XCTAssertEqual(collection.numberOfItems(inSection: 0), 1)
        XCTAssertEqual(collection.numberOfItems(inSection: 1), 17)
        XCTAssertEqual(collection.numberOfItems(inSection: 2), 0)
        var selected: PostEditorMedia?
        picker.onSelect = { selected = $0 }
        var create = false
        picker.onCreateSticker = { create = true }
        collection.delegate?.collectionView?(collection, didSelectItemAt: IndexPath(item: 0, section: 0))
        XCTAssertTrue(create)
        XCTAssertNil(selected)
        collection.delegate?.collectionView?(collection, didSelectItemAt: IndexPath(item: 11, section: 1))
        let media = try XCTUnwrap(selected)
        XCTAssertEqual(media.provider, "aup")
        XCTAssertEqual(media.providerID, "aup-12")
        XCTAssertEqual(media.mimeType, "image/webp")
        guard case .object(let payload) = media.payload else { return XCTFail("Expected attachment") }
        XCTAssertEqual(payload["url"], .string(media.url))
        XCTAssertEqual(payload["mime_type"], .string("image/webp"))
        picker.stop()
    }

    func testGifUsesSmallAnimationAndPreservesTheStillForPublishedThumbnails() throws {
        let media = try gif([
            "sm": ["gif": file("small.gif")],
            "xs": ["jpg": file("still.jpg")]
        ])
        XCTAssertEqual(media.url, "https://static.klipy.com/small.gif")
        XCTAssertEqual(media.previewURL, "https://static.klipy.com/small.gif")
        XCTAssertEqual(media.loadingPreviewURL, "https://static.klipy.com/still.jpg")
        XCTAssertEqual(media.width, 480)
        XCTAssertEqual(media.height, 360)
        guard case let .object(payload) = media.payload else { return XCTFail("Expected media payload") }
        XCTAssertEqual(payload["url"], .string(media.url))
        XCTAssertEqual(payload["thumbnail_url"], .string("https://static.klipy.com/still.jpg"))
        XCTAssertNil(payload["loading_preview_url"])
        XCTAssertNil(payload["search_query"])
    }

    func testGifFallbackSkipsInvalidPreviewAndUsesOriginalWhenNeeded() throws {
        let small = try gif(["sm": ["gif": file("insecure.gif", scheme: "http")], "xs": ["gif": file("tiny.gif")]])
        XCTAssertEqual(small.previewURL, "https://static.klipy.com/tiny.gif")
        let original = try gif([:])
        XCTAssertEqual(original.previewURL, original.url)
        XCTAssertNil(original.loadingPreviewURL)
    }

    func testStillAcceptsJPGAndPNGWithoutUsingAnimatedContentAsAPlaceholder() throws {
        for format in ["jpg", "png"] {
            XCTAssertEqual(try gif(["xs": [format: file("still.\(format)")]]).loadingPreviewURL,
                           "https://static.klipy.com/still.\(format)")
        }
    }

    func testKlipyMemeKeepsPNGOriginalAndSmallPreview() throws {
        let data = try JSONSerialization.data(withJSONObject: ["result": true, "data": ["current_page": 1, "has_next": true,
            "data": [["slug": "meme-id", "title": "Meme", "type": "static_meme", "file": [
                "hd": ["png": file("original.png")], "sm": ["png": file("small.png")]
            ]]]]])
        let page = try APIJSON.decode(KlipyResponse.self, from: data).mediaPage(kind: .meme, query: "ciao")
        let media = try XCTUnwrap(page.items.first)
        XCTAssertEqual(media.url, "https://static.klipy.com/original.png")
        XCTAssertEqual(media.previewURL, "https://static.klipy.com/small.png")
        XCTAssertEqual(media.mimeType, "image/png")
        XCTAssertEqual(media.provider, "klipy")
        XCTAssertEqual(media.providerID, "meme-id")
        XCTAssertEqual(media.searchQuery, "ciao")
        XCTAssertEqual(page.nextOffset, 2)
    }

    func testMalformedOriginalIsNotSelectable() {
        XCTAssertThrowsError(try gif(["hd": ["gif": file("invalid.gif", scheme: "http")]]))
    }

    func testCellStartsSpinnerAndReuseStopsAndClearsEverything() throws {
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        let controller = UIViewController()
        window.rootViewController = controller
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        let cell = RemoteMediaCell(frame: CGRect(x: 0, y: 0, width: 120, height: 112))
        controller.view.addSubview(cell)
        let media = try gif([:])
        cell.configure(media)
        let imageView = try XCTUnwrap(cell.contentView.subviews.compactMap { $0 as? PostMediaImageView }.first)
        let spinner = try XCTUnwrap(imageView.subviews.compactMap { $0 as? UIActivityIndicatorView }.first)
        XCTAssertTrue(spinner.isAnimating)
        cell.configure(media) // Rebinding the same item must not restart its request.
        XCTAssertTrue(spinner.isAnimating)
        cell.prepareForReuse() // Synchronous cancellation, before either task can start a download.
        XCTAssertFalse(spinner.isAnimating)
        for image in cell.contentView.subviews.compactMap({ $0 as? UIImageView }) {
            XCTAssertNil(image.image)
            XCTAssertFalse(image.isAnimating)
        }
    }

    private func file(_ name: String, scheme: String = "https") -> [String: Any] {
        ["url": "\(scheme)://static.klipy.com/\(name)", "width": 480, "height": 360]
    }
    private func gif(_ renditions: [String: [String: [String: Any]]]) throws -> PostEditorMedia {
        var formats = renditions
        if formats["hd"] == nil { formats["hd"] = ["gif": file("original.gif")] }
        let data = try JSONSerialization.data(withJSONObject: ["result": true, "data": ["current_page": 1, "has_next": false,
            "data": [["slug": "gif-id", "title": "GIF", "type": "gif", "file": formats]]]])
        return try XCTUnwrap(APIJSON.decode(KlipyResponse.self, from: data).mediaPage(kind: .gif, query: "").items.first)
    }
}
