import XCTest

final class AuthFlowTests: XCTestCase {
    func testSignedOutLaunchAndReturnFromLogin() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["--ui-test-signed-out"]
        app.launch()

        let start = app.buttons["intro-start"]
        XCTAssertTrue(start.waitForExistence(timeout: 10))
        start.tap()

        let email = app.textFields["email-field"]
        XCTAssertTrue(email.waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["send-code"].isEnabled)

        app.buttons["auth-back"].tap()
        XCTAssertTrue(start.waitForExistence(timeout: 5))
    }
}
