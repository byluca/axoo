import XCTest
@testable import Axoo

final class KlipyResponseTests: XCTestCase {
    func testDeliveryURLsSlugAndPaginationSurviveMapping() throws {
        let response = try APIJSON.decode(KlipyResponse.self, from: fixture())
        let page = try response.mediaPage(kind: .gif, query: "  ciao!  ")
        let item = try XCTUnwrap(page.items.first)
        XCTAssertEqual(item.provider, "klipy")
        XCTAssertEqual(item.providerID, "ciao-amici")
        XCTAssertEqual(item.searchQuery, "  ciao!  ")
        XCTAssertEqual(item.url, "https://static.klipy.com/small.gif")
        XCTAssertEqual(item.previewURL, "https://static.klipy.com/small.gif")
        XCTAssertEqual(item.width, 200)
        XCTAssertEqual(item.height, 150)
        XCTAssertEqual(page.nextOffset, 3)
    }

    func testUnsupportedContentAndUntrustedHostFailWithoutFilteringThePage() throws {
        let ad = try APIJSON.decode(KlipyResponse.self, from: fixture(type: "ad"))
        XCTAssertThrowsError(try ad.mediaPage(kind: .gif, query: ""))
        let foreign = try APIJSON.decode(KlipyResponse.self, from: fixture(host: "static.klipy.com.evil.example"))
        XCTAssertThrowsError(try foreign.mediaPage(kind: .gif, query: ""))
    }

    func testStickerUsesItsOwnKindAndTheLastPageStops() throws {
        let response = try APIJSON.decode(KlipyResponse.self, from: fixture(type: "sticker", next: false))
        let page = try response.mediaPage(kind: .sticker, query: "")
        XCTAssertEqual(page.items.first?.kind, .sticker)
        XCTAssertNil(page.nextOffset)
    }

    private func fixture(type: String = "gif", host: String = "static.klipy.com", next: Bool = true) throws -> Data {
        try JSONSerialization.data(withJSONObject: ["result": true, "data": ["current_page": 2, "has_next": next,
            "data": [["id": 6914457558367152, "slug": "ciao-amici", "title": "Ciao", "type": type, "file": [
                "hd": ["gif": ["url": "https://\(host)/original.gif?delivery=keep", "width": 320, "height": 240]],
                "sm": ["gif": ["url": "https://\(host)/small.gif", "width": 200, "height": 150]]
            ]]]]])
    }
}
