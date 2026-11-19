package com.axoo.ui.sheets

import android.widget.TextView
import com.axoo.ui.theme.AppFontFamily
import com.axoo.ui.theme.axooTypeface

internal data class BottomSheetTextStyle(val sizeSp: Float, val family: AppFontFamily, val weight: Int)

/** Base sizes and weights from the matching UIKit sheets, before system text scaling. */
internal object BottomSheetTypography {
    val title = BottomSheetTextStyle(22f, AppFontFamily.BALOO2, 700)
    val body = BottomSheetTextStyle(14f, AppFontFamily.INTER, 400)
    val action = BottomSheetTextStyle(18f, AppFontFamily.BALOO2, 700)
    val option = BottomSheetTextStyle(17f, AppFontFamily.BALOO2, 600)
    val confirmationTitle = title.copy(sizeSp = 18f)
    val confirmationAction = option.copy(sizeSp = 15f)
    val search = BottomSheetTextStyle(16f, AppFontFamily.INTER, 400)
    val avatarSave = BottomSheetTextStyle(16f, AppFontFamily.BALOO2, 700)
    val avatarError = BottomSheetTextStyle(13f, AppFontFamily.BALOO2, 600)
    val avatarSection = BottomSheetTextStyle(13f, AppFontFamily.BALOO2, 700)
    val avatarChoice = BottomSheetTextStyle(12f, AppFontFamily.BALOO2, 700)
}

internal fun TextView.applySheetTypography(style: BottomSheetTextStyle) {
    textSize = style.sizeSp
    typeface = context.axooTypeface(style.family, style.weight)
    includeFontPadding = false
    letterSpacing = 0f
}
