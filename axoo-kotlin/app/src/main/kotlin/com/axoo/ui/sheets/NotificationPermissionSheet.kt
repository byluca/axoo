package com.axoo.ui.sheets

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp

@SuppressLint("ViewConstructor")
class NotificationPermissionSheet(
    context: Context,
    onEnable: () -> Unit,
) : BottomSheetContainer(context) {
    init {
        setSheetContent(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(32))

            addView(makeTitle(OverlayCopy.notificationPermissionTitle).apply {
                gravity = Gravity.CENTER
            })
            addView(makeBody(OverlayCopy.notificationPermissionMessage).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(20))
            })
            addView(makeActionText(OverlayCopy.enableNotifications).apply {
                gravity = Gravity.CENTER
                minHeight = dp(48)
                setTextColor(NativeColors.accent)
                setOnClickListener { dismissThen(onEnable) }
            })
            addView(makeActionText(OverlayCopy.notificationsNotNow).apply {
                gravity = Gravity.CENTER
                minHeight = dp(48)
                setTextColor(NativeColors.textSecondary)
                setOnClickListener { dismiss() }
            })
        })
    }
}
