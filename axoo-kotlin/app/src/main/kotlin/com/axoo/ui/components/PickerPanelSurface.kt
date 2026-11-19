package com.axoo.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import com.axoo.ui.theme.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
open class PickerPanelSurface(
    context: Context,
    backgroundColor: Int,
    private val cornerRadiusDp: Int = CORNER_RADIUS_DP,
) : FrameLayout(context) {
    private val contentClip = Path()
    init {
        background = GradientDrawable().apply {
            setColor(backgroundColor)
            cornerRadius = context.dp(cornerRadiusDp).toFloat()
        }
        // Keep the outline above category backgrounds and the bottom fade.
        foreground = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = context.dp(cornerRadiusDp).toFloat()
            setStroke(context.dp(NativePillDefaults.borderWidthDp).toInt().coerceAtLeast(1), NativePillDefaults.borderColor)
        }
        setCardShadow(context.dp(cornerRadiusDp).toFloat())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val radius = context.dp(cornerRadiusDp).toFloat()
        contentClip.reset()
        contentClip.addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val save = canvas.save()
        canvas.clipPath(contentClip)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }

    companion object {
        const val CORNER_RADIUS_DP = NativePillDefaults.controlCornerRadiusDp
    }
}

fun Context.defaultPickerPanelHeight(): Int {
    val screenHeight = resources.displayMetrics.heightPixels
    return max(dp(240), min(dp(320), (screenHeight * 0.34f).roundToInt()))
}
