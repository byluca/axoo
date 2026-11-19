import XCTest
import UIKit
@testable import Axoo

@MainActor
final class ProfileBioTests: XCTestCase {
    private let initial = Data(#"{"id":"me","nickname":"me","university_id":"campus","bio":"ciao","bio_entities":[]}"#.utf8)

    func testBioDecodesWithoutRequiringTheNewFieldsInOldResponses() throws {
        let old = try APIJSON.decode(AccountProfile.self, from: Data(#"{"id":"me","nickname":"me","university_id":"campus"}"#.utf8))
        XCTAssertEqual(old.bioDocument, EmojiDocument(text: ""))
        let data = Data(#"{"id":"me","nickname":"me","university_id":"campus","bio":"😀 ciao","bio_entities":[{"type":"custom_emoji","offset":0,"length":1,"custom_emoji_id":42}]}"#.utf8)
        let user = try APIJSON.decode(AccountProfile.self, from: data)
        XCTAssertEqual(user.bioDocument.entities, [EmojiEntity(offset: 0, length: 1, id: 42)])
    }

    func testSaveTrimsTextAndPreservesScalarOffsetsAndEmojiIDs() async throws {
        let api = BioAPI(response: initial)
        let repository = ProfileRepository(api: api, currentUser: try APIJSON.decode(AccountProfile.self, from: initial))
        let draft = EmojiDocument(text: "  😀 ciao  ", entities: [EmojiEntity(offset: 2, length: 1, id: 42)])
        _ = try await repository.updateBio(draft)
        let payload = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(api.bodies.last!)) as? [String: Any])
        XCTAssertEqual(payload["bio"] as? String, "😀 ciao")
        let entities = try XCTUnwrap(payload["bio_entities"] as? [[String: Any]])
        XCTAssertEqual(entities.first?["offset"] as? Int, 0)
        XCTAssertEqual(entities.first?["length"] as? Int, 1)
        XCTAssertEqual(entities.first?["custom_emoji_id"] as? Int, 42)
        XCTAssertEqual(api.paths, ["users/me/bio"])
        XCTAssertEqual(repository.currentUser?.bioDocument, draft.normalized)
    }

    func testEntityOnlyChangesCanBeSavedAndFailedSaveKeepsDraft() async throws {
        let api = BioAPI(response: initial)
        let model = ProfileViewModel(repository: ProfileRepository(api: api, currentUser: try APIJSON.decode(AccountProfile.self, from: initial)))
        model.beginBioEditing()
        XCTAssertFalse(model.canSaveBio)
        let document = EmojiDocument(text: "ciao", entities: [EmojiEntity(offset: 0, length: 4, id: 42)])
        model.changeBio(document)
        XCTAssertTrue(model.canSaveBio)
        api.fail = true
        var saves = 0
        model.saveBio { saves += 1 }
        await model.bioTask?.value
        XCTAssertEqual(saves, 0)
        XCTAssertEqual(model.state.bio, document)
        XCTAssertEqual(model.user?.bioDocument, EmojiDocument(text: "ciao"))
        XCTAssertTrue(model.canSaveBio)
        XCTAssertNotNil(model.state.bioError)
        api.fail = false
        model.saveBio { saves += 1 }
        model.saveBio { saves += 1 }
        await model.bioTask?.value
        XCTAssertEqual(saves, 1)
        XCTAssertEqual(model.user?.bioDocument, document)
        XCTAssertFalse(model.canSaveBio)
        model.stop()
    }

    func testLimitCountsScalarsAndRejectsInvalidEntityRanges() {
        XCTAssertNil(AccountProfile.bioValidationError(EmojiDocument(text: String(repeating: "😀", count: 200))))
        XCTAssertNotNil(AccountProfile.bioValidationError(EmojiDocument(text: String(repeating: "😀", count: 201))))
        XCTAssertNotNil(AccountProfile.bioValidationError(EmojiDocument(text: "ciao", entities: [EmojiEntity(offset: 4, length: 1, id: 42)])))
    }

    func testPublicProfileUsesUserEndpointAndSkeletonIsReplacedOnSuccess() async throws {
        let api = BioAPI(response: Data(#"{"id":"other","nickname":"other","university_id":"campus","bio":"ciao"}"#.utf8))
        let posts = PostRepository(profile: ProfileRepository(api: api))
        let model = PublicProfileViewModel(userID: "other", posts: posts)
        let list = MyPostsViewModel(source: posts, page: { _ in HomeFeedPage(posts: [], nextCursor: nil) })
        let host = PublicProfileHost(viewModel: model, postsModel: list, editorService: posts,
            makeDetail: { _ in fatalError("Detail is not opened by this fixture") }, makeBioEditor: { UIViewController() })
        host.loadViewIfNeeded()
        XCTAssertFalse(try XCTUnwrap(find("profile-skeleton", in: host.view)).isHidden)
        model.load()
        await model.loadTask?.value
        XCTAssertEqual(api.paths, ["users/other"])
        XCTAssertEqual(model.user?.nickname, "other")
        XCTAssertFalse(model.isLoading)
        XCTAssertNil(find("profile-skeleton", in: host.view))
        model.stop()
    }

    func testBioPillHeightDoesNotMoveToggleAwayFromFirstLine() throws {
        let api = BioAPI(response: initial)
        let model = ProfileViewModel(repository: ProfileRepository(api: api, currentUser: try APIJSON.decode(AccountProfile.self, from: initial)))
        let host = BioEditorHost(viewModel: model)
        host.loadViewIfNeeded()
        host.view.frame = CGRect(x: 0, y: 0, width: 390, height: 844)
        host.view.layoutIfNeeded()
        let pill = try XCTUnwrap(find("bio-pill", in: host.view))
        let toggle = try XCTUnwrap(find("emoji-keyboard-toggle", in: pill))
        let input = try XCTUnwrap(find("bio-input", in: pill) as? EmojiTextView)
        XCTAssertGreaterThanOrEqual(pill.bounds.height, 120)
        XCTAssertEqual(toggle.frame.minY, 2, accuracy: 0.5)
        input.setDocument(EmojiDocument(text: String(repeating: "Ciao!\n", count: 8)))
        input.invalidateIntrinsicContentSize()
        host.view.layoutIfNeeded()
        XCTAssertEqual(toggle.frame.minY, 2, accuracy: 0.5)
        XCTAssertEqual(input.frame.minY, toggle.frame.minY, accuracy: 0.5)
        model.stop()
    }

    func testReportUsesUserTargetType() async throws {
        let api = BioAPI(response: initial)
        try await ProfileRepository(api: api).reportUser("other", reason: "spam")
        let payload = try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(api.bodies.last!)) as? [String: String])
        XCTAssertEqual(api.paths, ["reports"])
        XCTAssertEqual(payload, ["target_type": "user", "target_id": "other", "reason": "spam"])
    }

    private func find(_ id: String, in view: UIView) -> UIView? {
        if view.accessibilityIdentifier == id { return view }
        for child in view.subviews { if let result = find(id, in: child) { return result } }
        return nil
    }
}

@MainActor
private final class BioAPI: APIClient {
    var response: Data
    var fail = false
    var paths: [String] = []
    var bodies: [Data?] = []
    init(response: Data) { self.response = response }
    func request(_ method: String, path: String, query: [URLQueryItem], body: Data?) async throws -> Data {
        paths.append(path); bodies.append(body)
        if fail { throw URLError(.notConnectedToInternet) }
        if path == "users/me/bio", let body {
            var user = try XCTUnwrap(JSONSerialization.jsonObject(with: response) as? [String: Any])
            let payload = try XCTUnwrap(JSONSerialization.jsonObject(with: body) as? [String: Any])
            user["bio"] = payload["bio"]
            user["bio_entities"] = payload["bio_entities"]
            response = try JSONSerialization.data(withJSONObject: user)
        }
        return response
    }
}
