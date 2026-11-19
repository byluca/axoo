package com.axoo.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp

/**
 * Aspetto configurabile delle card arrotondate native.
 *
 * Le feature possono mantenere la stessa silhouette variando raggio, bordo,
 * separatori e altezza di ogni riga. L'ombra resta quella condivisa delle card post.
 */
internal data class NativePillStyle(
    val cornerRadiusDp: Float = 20f,
    val dividerHorizontalInsetDp: Int = 16,
    @ColorInt val dividerColor: Int = NativePillDefaults.dividerColor,
)

/** Shared surfaces follow the system palette; features only change the geometry. */
internal object NativePillDefaults {
    const val toolCornerRadiusDp = 6
    const val controlCornerRadiusDp = 22
    const val controlElevationDp = 1.5f
    val backgroundColor: Int get() = NativeColors.card
    val borderColor: Int get() = NativeColors.cardBorder
    const val borderWidthDp: Float = 0.7f
    val dividerColor: Int get() = NativeColors.divider
    val pressedOverlayColor: Int get() = NativeColors.pressedOverlay
}

/**
 * Applica la superficie condivisa anche a View che non contengono righe.
 * I colori opzionali servono alle varianti semantiche, come i flair dei post.
 */
internal fun View.applyNativePillSurface(
    cornerRadiusDp: Float,
    @ColorInt backgroundColor: Int = NativePillDefaults.backgroundColor,
    @ColorInt borderColor: Int? = NativePillDefaults.borderColor,
) {
    background = GradientDrawable().apply {
        setColor(backgroundColor)
        cornerRadius = dp(cornerRadiusDp)
        borderColor?.let { color ->
            setStroke(
                dp(NativePillDefaults.borderWidthDp).toInt().coerceAtLeast(1),
                color,
            )
        }
    }
    setCardShadow(dp(cornerRadiusDp))
}

/** Contenitore verticale per righe native con una silhouette di pillola condivisa. */
internal open class NativePillCard(
    context: Context,
    private val style: NativePillStyle = NativePillStyle(),
) : LinearLayout(context) {
    private val contentClip = Path()
    init {
        orientation = VERTICAL
        applyNativePillSurface(style.cornerRadiusDp)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val radius = dp(style.cornerRadiusDp)
        contentClip.reset()
        contentClip.addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val save = canvas.save()
        canvas.clipPath(contentClip)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }

    fun addPillRow(row: View, heightDp: Int = LayoutParams.WRAP_CONTENT) {
        if (childCount > 0) {
            addView(
                View(context).apply { setBackgroundColor(style.dividerColor) },
                LayoutParams(LayoutParams.MATCH_PARENT, dp(1)).apply {
                    leftMargin = dp(style.dividerHorizontalInsetDp)
                    rightMargin = dp(style.dividerHorizontalInsetDp)
                },
            )
        }
        val height = if (heightDp == LayoutParams.WRAP_CONTENT) heightDp else dp(heightDp)
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, height))
    }
}
