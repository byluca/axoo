package com.axoo.ui.theme

import android.content.res.Resources
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.annotation.MainThread
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import com.axoo.R

/** Android selects values/values-night. Views are recreated on theme changes. */
object NativeColors {
    private lateinit var resources: Resources

    fun initialize(resources: Resources) {
        this.resources = resources
    }

    /** Synchronous export only: restore the app palette before the next frame. */
    @MainThread
    internal fun <T> withLightColors(context: Context, draw: () -> T): T {
        val previous = resources
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }
        resources = context.createConfigurationContext(configuration).resources
        return try { draw() } finally { resources = previous }
    }

    @ColorInt private fun color(@ColorRes id: Int): Int = resources.getColor(id, null)

    @ColorInt val white: Int = Color.WHITE
    @ColorInt val scrim: Int = Color.BLACK
    val background: Int get() = color(R.color.axoo_background)
    val surface: Int get() = color(R.color.axoo_surface)
    val card: Int get() = color(R.color.axoo_card)
    val panel: Int get() = color(R.color.axoo_panel)
    val editorialPaper: Int get() = color(R.color.axoo_editorial_paper)
    val pickerBackground: Int get() = color(R.color.axoo_picker_background)
    val pickerSurface: Int get() = card
    val pickerControl: Int get() = color(R.color.axoo_picker_control)
    val categorySelection: Int get() = color(R.color.axoo_category_selection)
    val navigationSelection: Int get() = color(R.color.axoo_navigation_selection)

    val textPrimary: Int get() = color(R.color.axoo_text_primary)
    val textSecondary: Int get() = color(R.color.axoo_text_secondary)
    val interactionInactive: Int get() = color(R.color.axoo_interaction_inactive)
    val border: Int get() = color(R.color.axoo_border)
    val cardBorder: Int get() = color(R.color.axoo_card_border)
    val divider: Int get() = color(R.color.axoo_divider)
    val pressedOverlay: Int get() = color(R.color.axoo_pressed_overlay)
    val skeleton: Int get() = color(R.color.axoo_skeleton)
    val link: Int get() = color(R.color.axoo_link)

    val accent: Int get() = color(R.color.axoo_accent)
    @ColorInt val campusStarPurple: Int = 0xFF7C3AED.toInt()
    @ColorInt val campusStarMiddle: Int = 0xFFC026D3.toInt()
    @ColorInt val textSelection: Int = 0x38FF00BF
    val onAccent: Int get() = color(R.color.axoo_on_accent)
    val downvoteActive: Int get() = color(R.color.axoo_downvote_active)
    val destructive: Int get() = color(R.color.axoo_destructive)
    val onDestructive: Int get() = color(R.color.axoo_on_destructive)

    val moderationPositiveBg: Int get() = color(R.color.axoo_moderation_positive_bg)
    val moderationPositiveText: Int get() = color(R.color.axoo_moderation_positive_text)
    val moderationWarningBg: Int get() = color(R.color.axoo_moderation_warning_bg)
    val moderationWarningText: Int get() = color(R.color.axoo_moderation_warning_text)
    val moderationDangerBg: Int get() = color(R.color.axoo_moderation_danger_bg)
}
