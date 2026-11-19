package com.axoo.ui.sheets

import androidx.annotation.ColorInt
import com.axoo.ui.theme.NativeColors

/** Content and dismissal options; gestures, states and timing are provided by Material. */
data class BottomSheetConfig(
    val dismissOnScrimClick: Boolean = true,
    val dragToDismiss: Boolean = true,
    val showsDragHandle: Boolean = true,
    /** Media grids fill the viewport and use Material half/fully expanded states. */
    val fillsHeight: Boolean = false,
    @ColorInt val sheetBackgroundColor: Int = NativeColors.panel,
)
