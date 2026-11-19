import XCTest
@testable import Axoo

final class AvatarIdentityTests: XCTestCase {
    func testAPIDecodingPreservesOfficialAccountMetadata() throws {
        struct Response: Decodable { let avatarConfig: AvatarConfiguration }
        for account in ["axoo", "aup"] {
            let data = try JSONSerialization.data(withJSONObject: ["avatar_config": [
                "official_account": account, "hairColor": "b9a05f", "futureMetadata": 7
            ]])
            let configuration = try APIJSON.decode(Response.self, from: data).avatarConfig
            XCTAssertEqual(configuration.values["official_account"], account)
            XCTAssertEqual(configuration.values["hairColor"], "b9a05f")
            XCTAssertEqual(configuration.values["futureMetadata"], "")
            XCTAssertTrue(configuration.isVerified)
            let identity = AvatarIdentity(seed: account, configuration: configuration)
            XCTAssertEqual(identity.imageURL, configuration.officialAvatarURL)
            XCTAssertEqual(identity.svgURL, configuration.officialAvatarURL)
            XCTAssertEqual(identity.imageURL?.lastPathComponent, account == "axoo" ? "axoo.png" : "aup.jpg")
        }
    }

    func testAndroidDefaultParametersAndUTF16SeedHash() {
        XCTAssertEqual(AvatarIdentity(seed: "alice").parameters, [
            "hairVariant": "short18", "hairColor": "cb6820", "backgroundColor": "6a4c93",
            "eyesVariant": "variant18", "eyebrowsVariant": "variant02", "mouthVariant": "variant17",
            "skinColor": "f2d3b1", "earringsProbability": "0", "glassesProbability": "0", "detailsProbability": "0"
        ])
        // Supplementary Unicode characters occupy two Kotlin Char values.
        let unicode = AvatarIdentity(seed: "studente-😀").parameters
        XCTAssertEqual(unicode["hairVariant"], "long09")
        XCTAssertEqual(unicode["hairColor"], "796a45")
        XCTAssertEqual(unicode["eyesVariant"], "variant04")
        XCTAssertEqual(unicode["mouthVariant"], "variant16")
    }
    func testSavedOptionsOverrideDefaultsAndRejectInvalidValues() throws {
        let data = Data(#"{"hairVariant":"short01","skinColor":"9e5622","glassesVariant":"variant02","eyesVariant":"invalid","futureMetadata":7}"#.utf8)
        let saved = try JSONDecoder().decode(AvatarConfiguration.self, from: data)
        let params = AvatarIdentity(seed: "alice", configuration: saved).parameters
        XCTAssertEqual(params["hairVariant"], "short01")
        XCTAssertEqual(params["skinColor"], "9e5622")
        XCTAssertEqual(params["glassesProbability"], "100")
        XCTAssertEqual(params["earringsProbability"], "0")
        XCTAssertEqual(params["eyesVariant"], "variant18")
        XCTAssertNil(params["futureMetadata"])
    }
    func testSavedConfigurationRemovesUnspecifiedOptionalParts() {
        // Find a default with an optional part, without depending on a single seed.
        let defaults = (0..<1000).map { AvatarIdentity(seed: "user-\($0)") }
        let withGlasses = defaults.first { $0.parameters["glassesProbability"] == "100" }!
        let saved = AvatarIdentity(seed: withGlasses.seed, configuration: AvatarConfiguration(values: ["hairColor": "0e0e0e"]))
        XCTAssertEqual(saved.parameters["glassesProbability"], "0")
        XCTAssertNil(saved.parameters["glassesVariant"])
        XCTAssertEqual(AvatarIdentity(seed: withGlasses.seed, configuration: AvatarConfiguration(values: [:])).parameters, withGlasses.parameters)
    }
    func testURLRoundTripsSeedAndHasStableCacheIdentity() throws {
        let identity = AvatarIdentity(seed: "a&b + 😀")
        let url = try XCTUnwrap(identity.imageURL)
        let parts = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))
        XCTAssertEqual(parts.host, "api.dicebear.com")
        XCTAssertEqual(parts.path, "/10.x/adventurer/png")
        XCTAssertEqual(parts.queryItems?.first { $0.name == "seed" }?.value, identity.seed)
        XCTAssertEqual(parts.queryItems?.first { $0.name == "size" }?.value, "128")
        XCTAssertEqual(url, identity.imageURL)
        XCTAssertNil(AvatarIdentity(seed: "").imageURL)
    }
}
