import UIKit

/// Base measures shared with Android's PostEditorStyle; fonts live in AxooTypography.
enum PostEditorStyle {
    static let outerInset: CGFloat = 12
    static let topInset: CGFloat = 8
    static let contentInset: CGFloat = 16
    static let sectionSpacing: CGFloat = 12
    static let categorySpacing: CGFloat = 4
    static let textVerticalInset: CGFloat = 10
    static let minimumTextHeight: CGFloat = 220
    static let pollMinimumTextHeight: CGFloat = minimumTextHeight - 48
    static let cardRadius: CGFloat = 20
    static let avatarSize: CGFloat = 40
    static let avatarTextSpacing: CGFloat = 10
    static let metadataSpacing: CGFloat = 6
    static let toolbarHeight: CGFloat = 40
    static let toolbarRadius: CGFloat = AxooTheme.toolPillRadius
    static let mediaRadius: CGFloat = 16
    static let maximumMediaSize: CGFloat = 220
    static let publishHeight: CGFloat = 42
    static let publishRadius: CGFloat = 12
    static let publishHorizontalInset: CGFloat = 28
}

/// Same rounded surface as Android's NativePostEditorBodyView.
final class PostEditorSurface: AxooDrawingView {
    override func draw(_ rect: CGRect) {
        let path = UIBezierPath(roundedRect: bounds.insetBy(dx: 0.35, dy: 0.35), cornerRadius: PostEditorStyle.cardRadius)
        AxooTheme.cardBackground.setFill()
        path.fill()
        AxooTheme.cardBorder.setStroke()
        path.lineWidth = 0.7
        path.stroke()
    }
}
