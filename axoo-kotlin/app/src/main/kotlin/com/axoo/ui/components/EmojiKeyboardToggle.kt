/*
 * NativeEmojiKeyboardToggleView
 *
 * Toggle leggero per passare da tastiera a picker emoji. Usa gli stessi SVG ufficiali
 * del composer commenti, così le superfici di scrittura restano visivamente allineate.
 */
package com.axoo.ui.components

import com.axoo.ui.theme.NativeColors
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import coil.load
import com.axoo.ui.theme.dp

@SuppressLint("ViewConstructor")
internal class NativeEmojiKeyboardToggleView(context: Context) : FrameLayout(context) {
    private val icon = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }
    private var pickerOpen = false
    private var tint = NativeColors.textPrimary
    private var iconSizePx = context.dp(28f)
    private var hitSizePx = context.dp(44f)
    private var currentIconAsset: String? = null

    init {
        isClickable = true
        isFocusable = true
        addView(
            icon,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER),
        )
    }

    fun bind(
        pickerOpen: Boolean,
        tint: Int,
        iconSizePx: Float,
        hitSizePx: Float,
    ) {
        this.pickerOpen = pickerOpen
        this.tint = tint
        this.iconSizePx = iconSizePx
        this.hitSizePx = hitSizePx
        bindIcon()
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = hitSizePx.toInt().coerceAtLeast(dp(32))
        val childSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        icon.measure(childSpec, childSpec)
        setMeasuredDimension(size, size)
    }

    private fun bindIcon() {
        val nextIconAsset = if (pickerOpen) "keyboard.svg" else "smiling.svg"
        if (nextIconAsset != currentIconAsset) {
            currentIconAsset = nextIconAsset
            icon.load("file:///android_asset/icons/$nextIconAsset")
        }
        icon.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
        val inset = ((hitSizePx - iconSizePx) / 2f).toInt().coerceAtLeast(0)
        icon.setPadding(inset, inset, inset, inset)
    }

}
