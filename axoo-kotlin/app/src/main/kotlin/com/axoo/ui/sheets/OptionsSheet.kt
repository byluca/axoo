/*
 * OptionsSheet
 *
 * Menu contestuale riutilizzabile. Il pattern `Handle.present()` è un caposaldo del nostro
 * modo di fare UI: non dichiariamo sheet dormienti nell'XML che occupano memoria fin
 * dall'inizio. Li evochiamo a runtime e li distruggiamo quando spariscono.
 * `OptionsSheetView` non sa quasi nulla del chiamante se non i dati per bindare,
 * e gestiamo i click consumando e dismettendo (tramite il dialog). Totalmente 
 * decoupled, memory footprint basso, prevedibilità alta.
 */
package com.axoo.ui.sheets

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import com.axoo.ui.components.AxooToggle
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp

/**
 * Reusable native options sheet (title + list of actions + optional footer). Mounted through
 * the shared Material dialog adapter.
 */
data class OptionsSheetAction(
    val text: String,
    val iconAsset: String? = null,
    val trailingText: String? = null,
    val destructive: Boolean = false,
    val toggleChecked: Boolean? = null,
    val onToggle: ((Boolean) -> Unit)? = null,
    val onClick: () -> Unit = {},
)

object OptionsSheet {

    class Handle internal constructor(private val view: OptionsSheetView) {
        fun dismiss() {
            view.dismiss()
        }

        fun setToggleChecked(actionText: String, checked: Boolean) {
            view.setToggleChecked(actionText, checked)
        }
    }

    fun present(
        anchor: View,
        actions: List<OptionsSheetAction>,
        title: String? = null,
        footer: String? = null,
        onDismissed: () -> Unit = {},
    ): Handle {
        // Callers pass the screen host: an anchored popup has no room above or below it.
        // The shared Material sheet uses its own window, independent of the host's size.
        val sheet = OptionsSheetView(
            context = anchor.context,
            onDismissed = onDismissed,
        )
        sheet.bind(actions, title, footer)
        sheet.presentOn(anchor)
        return Handle(sheet)
    }
}

@SuppressLint("ViewConstructor")
class OptionsSheetView internal constructor(
    context: Context,
    onDismissed: () -> Unit,
) : BottomSheetContainer(
    context = context,
    config = BottomSheetConfig(
        sheetBackgroundColor = NativeColors.panel,
        dismissOnScrimClick = true,
        dragToDismiss = true,
    ),
) {
    private val togglesByAction = mutableMapOf<String, AxooToggle>()
    private val contentView = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(4), 0, dp(8))
    }

    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setSheetContent(contentView)
        addOnDismissedListener(onDismissed)
    }

    fun bind(
        actions: List<OptionsSheetAction>,
        title: String?,
        footer: String?,
    ) {
        contentView.removeAllViews()
        togglesByAction.clear()
        if (title != null) {
            contentView.addView(makeHeader(title))
        }
        actions.forEach { action ->
            contentView.addView(makeActionRow(action), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
        }
        if (footer != null) {
            contentView.addView(makeFooter(footer))
        }
    }

    fun setToggleChecked(actionText: String, checked: Boolean) {
        togglesByAction[actionText]?.setChecked(checked)
    }

    private fun makeActionRow(action: OptionsSheetAction): View {
        val label = makeActionText(action.text, action.destructive).apply {
            applySheetTypography(BottomSheetTypography.action)
        }
        val toggle = action.toggleChecked?.let { checked ->
            AxooToggle(context).apply {
                setChecked(checked, animated = false)
                setOnCheckedChangeListener { action.onToggle?.invoke(it) }
            }
        }
        if (toggle != null) togglesByAction[action.text] = toggle
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setPadding(dp(20), 0, dp(20), 0)
            background = makePressedBackground()
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (toggle != null) {
                    toggle.performClick()
                } else {
                    dismissThen(action.onClick)
                }
            }
        }
        action.iconAsset?.let { asset ->
            row.addView(
                ImageView(context).apply {
                    imageTintList = ColorStateList.valueOf(
                        if (action.destructive) NativeColors.destructive else NativeColors.textPrimary,
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    load("file:///android_asset/icons/${Uri.encode(asset, "/")}")
                },
                LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                    marginEnd = dp(16)
                },
            )
        }
        row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        action.trailingText?.let { value ->
            row.addView(
                TextView(context).apply {
                    text = value
                    applySheetTypography(BottomSheetTypography.body)
                    setTextColor(NativeColors.textSecondary)
                    gravity = Gravity.CENTER_VERTICAL
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
        }
        toggle?.let {
            row.addView(it, LinearLayout.LayoutParams(dp(48), dp(40)).apply {
                marginStart = dp(12)
            })
        }
        return row
    }

    private fun makeHeader(title: String): TextView {
        return TextView(context).apply {
            text = title
            setTextColor(NativeColors.textPrimary)
            applySheetTypography(BottomSheetTypography.title)
            setPadding(dp(24), dp(8), dp(24), dp(12))
        }
    }

    private fun makeFooter(footer: String): TextView {
        return TextView(context).apply {
            text = footer
            setTextColor(NativeColors.textSecondary)
            applySheetTypography(BottomSheetTypography.body)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(16), dp(24), 0)
        }
    }

    private fun makePressedBackground(): StateListDrawable {
        val pressed = GradientDrawable().apply {
            setColor(NativeColors.pressedOverlay)
        }
        val normal = ColorDrawable(Color.TRANSPARENT)
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }
}
