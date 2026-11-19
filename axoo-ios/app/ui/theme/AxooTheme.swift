import UIKit

enum AxooTheme {
    static let toolPillRadius: CGFloat = 6
    static let controlCornerRadius: CGFloat = 22
    static let controlBorderWidth: CGFloat = 0.7
    static let controlShadowOpacity: Float = 0.10
    static let controlShadowRadius: CGFloat = 3
    static let controlShadowOffset = CGSize.zero
    static let accent = color(0xFF00BF, 0xFF66D8)
    static let surface = color(0xF0F2F5, 0x101014)
    static let downVote = color(0x00B4D8, 0x00B4D8)
    static let background = color(0xFFFFFF, 0x101014)
    static let cardBackground = color(0xFFFFFF, 0x1D1D24)
    static let panel = color(0xFFFFFF, 0x23232C)
    static let pickerBackground = color(0xFAF9FC, 0x101014)
    static let textPrimary = color(0x111014, 0xF6F4F8)
    static let textSecondary = color(0x6F6875, 0xABA8B8)
    static let interactionInactive = color(0x536471, 0xABA8B8)
    static let border = color(0xE1DCE5, 0x33333E)
    static let selection = color(0xEAEBED, 0x33333E)
    static let link = color(0x007AFF, 0xFF66D8)
    static let destructive = color(0xFF3B30, 0xFF8178)
    static let cardBorder = UIColor { traits in
        traits.userInterfaceStyle == .dark ? UIColor(rgb: 0x33333E) : UIColor.black.withAlphaComponent(0.08)
    }

    private static func color(_ light: UInt32, _ dark: UInt32) -> UIColor {
        UIColor { traits in UIColor(rgb: traits.userInterfaceStyle == .dark ? dark : light) }
    }

    static var isDark: Bool { UserDefaults.standard.bool(forKey: "darkTheme") }
    static var interfaceStyle: UIUserInterfaceStyle { isDark ? .dark : .light }

    @MainActor static func setDark(_ dark: Bool) {
        UserDefaults.standard.set(dark, forKey: "darkTheme")
        for scene in UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }) {
            for window in scene.windows { window.overrideUserInterfaceStyle = interfaceStyle }
        }
    }

}

extension UIColor {
    convenience init(rgb: UInt32) {
        self.init(red: CGFloat((rgb >> 16) & 255) / 255,
                  green: CGFloat((rgb >> 8) & 255) / 255,
                  blue: CGFloat(rgb & 255) / 255, alpha: 1)
    }
}
