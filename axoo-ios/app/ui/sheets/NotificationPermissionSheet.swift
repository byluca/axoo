import UIKit

final class NotificationPermissionSheet: AxooBottomSheet {
    init(onEnable: @escaping () -> Void) {
        super.init(title: AxooOverlayCopy.notificationPermissionTitle, showsHeading: false, showsCancel: false)
        let title = UILabel.axoo(AxooOverlayCopy.notificationPermissionTitle, font: AxooTypography.statusTitle)
        let body = UILabel.axoo(AxooOverlayCopy.notificationPermissionMessage,
            font: AxooTypography.statusBody, color: AxooTheme.textSecondary)
        title.textAlignment = .center
        body.textAlignment = .center
        appendContent(title)
        stack.setCustomSpacing(6, after: title)
        appendContent(body)
        stack.setCustomSpacing(20, after: body)
        let enable = addButton(AxooOverlayCopy.enableNotifications, testID: "enable-notifications", action: onEnable)
        enable.contentHorizontalAlignment = .center
        enable.configuration?.baseForegroundColor = AxooTheme.accent
        let later = addButton(AxooOverlayCopy.notificationsNotNow, testID: "notifications-not-now") { }
        later.contentHorizontalAlignment = .center
        later.configuration?.baseForegroundColor = AxooTheme.textSecondary
    }
}
