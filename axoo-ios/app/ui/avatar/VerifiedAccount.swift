import UIKit

extension AvatarConfiguration {
    var officialAvatarURL: URL? {
        let filename: String
        switch values["official_account"] {
        case "axoo": filename = "axoo-classic.png"
        case "aup": filename = "aup.jpg"
        default: return nil
        }
        return URL(string: "https://cyrfznxopjeeidcmfgze.supabase.co/storage/v1/object/public/memes/avatars/official/v1/" + filename)
    }
    var isVerified: Bool { officialAvatarURL != nil }
}

extension UILabel {
    func setVerifiedName(_ name: String, configuration: AvatarConfiguration?) {
        guard configuration?.isVerified == true, let image = UIImage(named: "icon-verification") else {
            attributedText = nil
            text = name
            accessibilityLabel = nil
            return
        }
        let size = min(20, font.pointSize)
        let attachment = NSTextAttachment()
        attachment.image = image.withRenderingMode(.alwaysOriginal)
        attachment.bounds = CGRect(x: 0, y: (font.capHeight - size) / 2, width: size, height: size)
        let result = NSMutableAttributedString(string: name + " ", attributes: [.font: font as Any])
        result.append(NSAttributedString(attachment: attachment))
        attributedText = result
        accessibilityLabel = name + ", account verificato"
    }
}
