import Foundation
import XCTest
@testable import Axoo

@MainActor
final class PersonalStickerRepositoryTests: XCTestCase {
    func testSaveSendsOriginalAndLibraryReturnsTheAccountsCopy() async throws {
        let api = StickerRecordingAPI()
        let repository = PersonalStickerRepository(api: api)
        let original = try XCTUnwrap(PostEditorMedia.fromPayload(APIJSON.decode(JSONValue.self, from: api.original)))
        let saved = try await repository.save(original)
        XCTAssertEqual(api.method, "POST")
        XCTAssertEqual(api.path, "media/stickers/save")
        let body = try APIJSON.decode(JSONValue.self, from: XCTUnwrap(api.body))
        XCTAssertEqual(body, original.payload)
        XCTAssertEqual(saved.providerID, original.providerID)
        XCTAssertNotEqual(saved.url, original.url)
        let library = try await repository.list()
        XCTAssertEqual(api.method, "GET")
        XCTAssertEqual(api.path, "media/stickers/mine")
        XCTAssertEqual(library, [saved])
        XCTAssertEqual(saved.kind, .sticker)
        XCTAssertEqual(saved.mimeType, "image/png")
    }

    func testPostCardKeepsCustomStickerMetadataForSaveAction() throws {
        let api = StickerRecordingAPI()
        let json = "{\"id\":\"p1\",\"user_id\":\"first\",\"created_at\":\"2026-10-24T10:00:00Z\",\"media\":\(String(decoding: api.original, as: UTF8.self))}"
        let post = try APIJSON.decode(PostRecord.self, from: Data(json.utf8))
        XCTAssertEqual(post.asPost().personalSticker?.provider, "axoo")
        XCTAssertEqual(post.asPost().personalSticker?.mimeType, "image/png")
        XCTAssertEqual(post.asPost().personalSticker?.url, post.mediaURL)
    }
}

@MainActor
private final class StickerRecordingAPI: APIClient {
    var method = ""
    var path = ""
    var body: Data?
    let original = Data(#"{"url":"https://example.test/first/id.png","width":512,"height":512,"type":"sticker","mime_type":"image/png","provider":"axoo","provider_id":"id"}"#.utf8)
    func request(_ method: String, path: String, query: [URLQueryItem], body: Data?) async throws -> Data {
        self.method = method; self.path = path; self.body = body
        let copy = String(decoding: original, as: UTF8.self).replacingOccurrences(of: "/first/", with: "/mine/")
        return Data((method == "GET" ? "{\"items\":[\(copy)]}" : copy).utf8)
    }
}
