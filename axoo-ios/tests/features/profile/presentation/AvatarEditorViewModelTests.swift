import XCTest
@testable import Axoo

@MainActor
final class AvatarEditorViewModelTests: XCTestCase {
    func testChangingOneOptionPreservesTheDisplayedAvatarThroughSavingAndReopening() throws {
        let identity = AvatarIdentity(seed: "real-user-😀")
        var saved: [String: String]?
        let editor = AvatarEditorViewModel(identity: identity) { saved = $0 }
        XCTAssertEqual(editor.preview.parameters, identity.parameters)
        XCTAssertFalse(editor.canSave)
        let hair = try XCTUnwrap(AvatarIdentity.options["hairVariant"]?.first { $0 != identity.parameters["hairVariant"] })
        editor.select("hairVariant", value: hair)
        var expected = identity.parameters
        expected["hairVariant"] = hair
        XCTAssertEqual(editor.preview.parameters, expected)
        editor.save()
        let restored = AvatarIdentity(seed: identity.seed, configuration: AvatarConfiguration(values: try XCTUnwrap(saved)))
        XCTAssertEqual(restored.parameters, editor.preview.parameters)
        let reopened = AvatarEditorViewModel(identity: restored) { _ in }
        XCTAssertEqual(reopened.preview.parameters, restored.parameters)
        XCTAssertFalse(reopened.canSave)
    }

    func testAnExistingDefaultAccessoryIsPreservedUntilExplicitlyRemoved() throws {
        let identity = try XCTUnwrap((0..<1000).map { AvatarIdentity(seed: "user-\($0)") }
            .first { $0.parameters["glassesProbability"] == "100" })
        let editor = AvatarEditorViewModel(identity: identity) { _ in }
        XCTAssertEqual(editor.preview.parameters, identity.parameters)
        editor.select("glassesVariant", value: nil)
        var expected = identity.parameters
        expected.removeValue(forKey: "glassesVariant")
        expected["glassesProbability"] = "0"
        XCTAssertEqual(editor.preview.parameters, expected)
    }

    func testPartialSavedConfigurationRetainsItsExistingAppearance() {
        let identity = AvatarIdentity(seed: "real-user", configuration: AvatarConfiguration(values: ["hairColor": "0e0e0e"]))
        let editor = AvatarEditorViewModel(identity: identity) { _ in }
        XCTAssertEqual(editor.preview.parameters, identity.parameters)
        XCTAssertEqual(editor.preview.parameters["hairColor"], "0e0e0e")
        XCTAssertFalse(editor.hasChanges)
    }

    func testFailedSaveKeepsTheDraftAndAllowsRetryWithoutDuplicateRequests() throws {
        let identity = AvatarIdentity(seed: "real-user")
        var saves: [[String: String]] = []
        let editor = AvatarEditorViewModel(identity: identity) { saves.append($0) }
        let hair = try XCTUnwrap(AvatarIdentity.options["hairVariant"]?.first { $0 != identity.parameters["hairVariant"] })
        editor.select("hairVariant", value: hair)
        let draft = editor.draft

        editor.save()
        editor.save()
        XCTAssertTrue(editor.isSaving)
        XCTAssertFalse(editor.canSave)
        XCTAssertEqual(saves, [draft])

        editor.saveFailed()
        XCTAssertFalse(editor.isSaving)
        XCTAssertEqual(editor.draft, draft)
        XCTAssertTrue(editor.canSave)

        editor.save()
        XCTAssertEqual(saves, [draft, draft])
        XCTAssertTrue(editor.isSaving)
    }
}
