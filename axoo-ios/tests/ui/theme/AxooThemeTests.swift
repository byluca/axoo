import XCTest
import UIKit
@testable import Axoo

@MainActor
final class AxooThemeTests: XCTestCase {
    func testPaletteMatchesAndroidInBothAppearances() {
        let colors: [(UIColor, UInt32, UInt32)] = [
            (AxooTheme.background, 0xFFFFFF, 0x151E27),
            (AxooTheme.surface, 0xF0F2F5, 0x151E27),
            (AxooTheme.cardBackground, 0xFFFFFF, 0x232E3B),
            (AxooTheme.panel, 0xFFFFFF, 0x242D39),
            (AxooTheme.textPrimary, 0x111014, 0xFAFAFA),
            (AxooTheme.textSecondary, 0x6F6875, 0x9AA7B4),
            (AxooTheme.accent, 0xFF00BF, 0xFF66D8),
            (AxooTheme.downVote, 0x00B4D8, 0x00B4D8),
            (AxooTheme.border, 0xE1DCE5, 0x324153),
        ]
        for (color, light, dark) in colors {
            for (style, expected) in [(UIUserInterfaceStyle.light, light), (.dark, dark)] {
                let resolved = color.resolvedColor(with: UITraitCollection(userInterfaceStyle: style))
                var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
                XCTAssertTrue(resolved.getRed(&r, green: &g, blue: &b, alpha: &a))
                let rgb = UInt32((r * 255).rounded()) << 16 | UInt32((g * 255).rounded()) << 8 | UInt32((b * 255).rounded())
                XCTAssertEqual(rgb, expected)
                XCTAssertEqual(a, 1)
            }
        }
    }

    func testSavedThemeDeterminesAppearanceAtNextLaunch() {
        let previous = AxooTheme.isDark
        defer { AxooTheme.setDark(previous) }
        AxooTheme.setDark(true)
        XCTAssertTrue(AxooTheme.isDark)
        XCTAssertEqual(AxooTheme.interfaceStyle, .dark)
        AxooTheme.setDark(false)
        XCTAssertFalse(AxooTheme.isDark)
        XCTAssertEqual(AxooTheme.interfaceStyle, .light)
    }
}
