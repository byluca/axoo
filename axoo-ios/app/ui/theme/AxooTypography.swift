import UIKit

/// Fixed fonts shared by native screens.
enum AxooTypography {
    static var homeLogo: UIFont { font("Baloo2-ExtraBold", size: 32) }
    static var feedTab: UIFont { font("Baloo2-ExtraBold", size: 17) }
    static var badge: UIFont { font("Baloo2-ExtraBold", size: 11) }
    static var navigationLabel: UIFont { font("Baloo2-Medium", size: 12) }
    static var statusTitle: UIFont { font("Baloo2-Bold", size: 22) }
    static var statusBody: UIFont { font("Inter-Regular", size: 14) }
    static var emptyStateBody: UIFont { font("Baloo2-SemiBold", size: 16) }
    static var postBody: UIFont { font("Inter-Regular", size: 16) }
    static var feedPostBody: UIFont { font("Inter-Regular", size: 17) }
    static var feedPostAuthor: UIFont { font("Baloo2-Bold", size: 16) }
    static var postAuthor: UIFont { font("Baloo2-Bold", size: 15) }
    static var postMetadata: UIFont { font("Baloo2-Regular", size: 13) }
    static var postScore: UIFont { font("Baloo2-Bold", size: 13) }
    static var postAction: UIFont { font("Baloo2-SemiBold", size: 13) }
    static var postCategory: UIFont { font("Baloo2-Bold", size: 11) }
    static var replyTitle: UIFont { font("Baloo2-SemiBold", size: 14) }
    static var replyBody: UIFont { font("Inter-Regular", size: 12) }
    static var editorTitle: UIFont { font("Baloo2-ExtraBold", size: 18) }
    static var editorAuthor: UIFont { font("Baloo2-Bold", size: 15) }
    static var editorMetadata: UIFont { font("Baloo2-Regular", size: 13) }
    static var editorBody: UIFont { font("Inter-Regular", size: 16) }
    static var editorPlaceholder: UIFont { editorBody }
    static var editorCategory: UIFont { postCategory }
    static var editorPublish: UIFont { font("Baloo2-Bold", size: 15) }
    static var editorCounter: UIFont { font("Inter-SemiBold", size: 12) }
    static var editorTool: UIFont { font("Baloo2-Medium", size: 11) }
    static var emojiCategory: UIFont { font("Baloo2-ExtraBold", size: 14) }
    static var profileNickname: UIFont { font("Baloo2-Bold", size: 24, fallbackWeight: .bold) }
    static var profileUniversity: UIFont { font("Baloo2-Regular", size: 18, fallbackWeight: .medium) }
    static var settingsVersion: UIFont { font("Baloo2-SemiBold", size: 14, fallbackWeight: .semibold) }
    static var sheetOption: UIFont { font("Baloo2-SemiBold", size: 17, fallbackWeight: .semibold) }
    static var sheetAction: UIFont { font("Baloo2-Bold", size: 18, fallbackWeight: .bold) }
    static var confirmationTitle: UIFont { font("Baloo2-Bold", size: 18, fallbackWeight: .bold) }
    static var confirmationAction: UIFont { font("Baloo2-SemiBold", size: 15, fallbackWeight: .semibold) }
    static var introTitleBrand: UIFont { font("Baloo2-ExtraBold", size: 48) }
    static var introTitle: UIFont { font("Baloo2-Bold", size: 34) }
    static var introSubtitle: UIFont { font("Inter-Regular", size: 18) }
    static var introSubtitleBold: UIFont { font("Inter-Bold", size: 18) }
    static var button: UIFont { font("Baloo2-Bold", size: 16) }
    static var primaryButton: UIFont { font("Baloo2-Bold", size: 18) }
    static var authCodeBody: UIFont { font("Inter-Regular", size: 13) }
    static var authCodeBold: UIFont { font("Inter-Bold", size: 13) }
    static var authButton: UIFont { font("Baloo2-Bold", size: 16) }
    static var authResend: UIFont { font("Baloo2-Bold", size: 15) }

    private static func font(_ name: String, size: CGFloat,
                             fallbackWeight: UIFont.Weight = .regular) -> UIFont {
        UIFont(name: name, size: size) ?? .systemFont(ofSize: size, weight: fallbackWeight)
    }
}
