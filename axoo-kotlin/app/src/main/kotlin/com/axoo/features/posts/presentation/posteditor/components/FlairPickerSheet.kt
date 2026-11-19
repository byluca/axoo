package com.axoo.features.posts.presentation.posteditor.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import coil.load
import com.axoo.application.navigation.host.OverlayHost
import com.axoo.features.posts.presentation.components.flairAppearance
import com.axoo.features.posts.presentation.components.flairDisplayLabel
import com.axoo.ui.sheets.BottomSheetConfig
import com.axoo.ui.sheets.BottomSheetContainer
import com.axoo.ui.sheets.BottomSheetTypography
import com.axoo.ui.sheets.OverlayCopy
import com.axoo.ui.sheets.applySheetTypography
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp

val kFlairs = listOf(
    "Meme", "Spotted", "Esami", "Confessioni",
    "Party",
)

/**
 * Native flair picker bottom sheet. Native-only.
 *
 * The sheet does not auto-dismiss on selection — the caller decides when to close it
 * (typically by reacting to the selected flair event). Use [Handle.bind] to update the
 * displayed selection while the sheet is still open.
 */
object FlairPickerSheet {

    class Handle internal constructor(private val view: NativeFlairPickerSheetView) {
        fun bind(selected: String?, includeTutti: Boolean) {
            view.bind(selected = selected, includeTutti = includeTutti)
        }

        fun dismiss() {
            view.dismiss()
        }
    }

    fun present(
        anchor: View,
        selected: String?,
        includeTutti: Boolean = false,
        onSelected: (String?) -> Unit,
        onDismissed: () -> Unit = {},
    ): Handle {
        val sheet = NativeFlairPickerSheetView(
            context = anchor.context,
            onSelected = onSelected,
            onDismissed = onDismissed,
        )
        sheet.bind(selected = selected, includeTutti = includeTutti)
        OverlayHost.presentSheetOn(anchor, sheet)
        return Handle(sheet)
    }
}

@SuppressLint("ViewConstructor")
internal class NativeFlairPickerSheetView(
    context: Context,
    private val onSelected: (String?) -> Unit,
    onDismissed: () -> Unit,
) : BottomSheetContainer(
    context = context,
    config = BottomSheetConfig(
        sheetBackgroundColor = NativeColors.background,
        dismissOnScrimClick = true,
        dragToDismiss = true,
    ),
) {
    private val contentColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), dp(20))
    }

    private var lastSelected: String? = null
    private var lastIncludeTutti: Boolean? = null

    init {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        setSheetContent(contentColumn)
        addOnDismissedListener(onDismissed)
    }

    fun bind(selected: String?, includeTutti: Boolean) {
        if (lastSelected == selected && lastIncludeTutti == includeTutti && contentColumn.childCount > 0) {
            return
        }
        lastSelected = selected
        lastIncludeTutti = includeTutti

        contentColumn.removeAllViews()
        contentColumn.addView(makeTitle(OverlayCopy.categoryTitle).apply {
            setPadding(0, 0, 0, dp(4))
        })
        contentColumn.addView(makeBody(OverlayCopy.categoryMessage).apply {
            setPadding(0, 0, 0, dp(12))
        })

        val options = buildList<String?> {
            if (includeTutti) add("Tutti") else add(null)
            addAll(kFlairs)
        }

        options.forEach { flair ->
            contentColumn.addView(
                makeFlairRow(
                    label = flair,
                    selected = selected == flair,
                ),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(52),
                ).apply {
                    bottomMargin = dp(4)
                },
            )
        }
    }

    private fun makeFlairRow(label: String?, selected: Boolean): View {
        val appearance = label?.let(::flairAppearance)
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val foreground = when {
            appearance == null -> NativeColors.textPrimary
            selected || !dark -> appearance.foreground
            else -> ColorUtils.blendARGB(appearance.foreground, appearance.background, 0.6f)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = makeRowBackground(
                selected = selected,
                selectedColor = appearance?.background ?: NativeColors.surface,
            )
            isClickable = true
            setOnClickListener {
                onSelected(label)
            }

            appearance?.iconName?.let { iconName ->
                addView(ImageView(context).apply {
                    imageTintList = ColorStateList.valueOf(foreground)
                    load("file:///android_asset/icons/$iconName.svg")
                }, LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                    rightMargin = dp(10)
                })
            }

            addView(TextView(context).apply {
                text = label?.let(::flairDisplayLabel) ?: OverlayCopy.uncategorized
                applySheetTypography(BottomSheetTypography.action)
                setTextColor(foreground)
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f,
            ))

        }
    }

    private fun makeRowBackground(selected: Boolean, selectedColor: Int): StateListDrawable {
        val radius = dp(18).toFloat()
        val pressedColor = NativeColors.pressedOverlay

        val normal = GradientDrawable().apply {
            cornerRadius = radius
            setColor(if (selected) selectedColor else Color.TRANSPARENT)
        }
        val pressed = GradientDrawable().apply {
            cornerRadius = radius
            setColor(if (selected) selectedColor else pressedColor)
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }
}
