import UIKit

/// Original Android SVGs compiled by Xcode's asset catalog, with template tint.
enum AxooIcon: String, CaseIterable {
    case emoji = "icon-smiling"
    case tearsOfJoy = "icon-tears-of-joy"
    case keyboard = "icon-keyboard"
    case backspace = "icon-backspace"
    case gif = "icon-gif"
    case gifSelected = "icon-gif-fill"
    case sticker = "icon-smiley-sticker"
    case stickerSelected = "icon-smiley-sticker-fill"
    case photo = "icon-photo"
    case poll = "icon-chart"
    case reply = "icon-reply-bold"
    case close = "icon-close"
    case send = "icon-send"
    case back = "icon-arrow-left"
    case commentUp = "icon-up-square-comment"
    case commentDown = "icon-down-square-comment"
    case postUp = "icon-arrow-up-v2"
    case postDown = "icon-arrow-down-v2"
    case comment = "icon-comment"
    case menu = "icon-dots-three"
    case hamburger = "icon-hamburger-menu"
    case avatar = "icon-user"
    case meme = "icon-smiling-filled"
    case spotted = "icon-heart-filled"
    case exams = "icon-book-filled"
    case confessions = "icon-messages"
    case party = "icon-party-filled"
    case residence = "icon-home2-filled"
    case home = "icon-home"
    case homeSelected = "icon-home-fill"
    case create = "icon-plus"
    case createSelected = "icon-plus-fill"
    case notifications = "icon-bell"
    case notificationsSelected = "icon-bell-fill"
    case profileSelected = "icon-user-filled"
    case error = "icon-error-triangle"
    case flag = "icon-flag"
    case block = "icon-close-square"
    case copy = "icon-copy"
    case share = "icon-share"
    case edit = "icon-pencil"
    case delete = "icon-delete"
    case blockedUsers = "icon-user-delete"
    case logout = "icon-logout"
    case contact = "icon-headphone"
    case privacy = "icon-lock"
    case terms = "icon-paper"
    case theme = "icon-moon"
    case info = "icon-info-circle"
    case cup = "icon-cup"
    case document = "icon-document"
    case star = "icon-star"
}

extension AxooIcon {
    var image: UIImage? {
        guard let image = UIImage(named: rawValue) else { return nil }
        return image.withRenderingMode(.alwaysTemplate)
    }

    func image(pointSize: CGFloat) -> UIImage? {
        guard let image = image else { return nil }
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: pointSize, height: pointSize))
        let rendered = renderer.image { _ in
            image.draw(in: CGRect(x: 0, y: 0, width: pointSize, height: pointSize))
        }
        return rendered.withRenderingMode(.alwaysTemplate)
    }
}

final class AxooIconView: UIImageView {
    init(icon: AxooIcon) {
        super.init(image: icon.image)
        contentMode = .scaleAspectFit
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("Programmatic UI only") }
}
