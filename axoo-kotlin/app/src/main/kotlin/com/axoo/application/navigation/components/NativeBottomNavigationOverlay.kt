package com.axoo.application.navigation.components

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.axoo.ui.components.BottomFadeView

/** Content scrolls behind the same bottom fade used by Home and Profile. */
internal class NativeBottomNavigationOverlay(
    context: Context,
    content: View,
    private val bar: NativeBottomNavigationBar,
    private val updateContentPadding: (Int) -> Unit,
) : FrameLayout(context) {
    private val fade = BottomFadeView(context)

    init {
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(fade, LayoutParams(LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        measureChild(bar, widthMeasureSpec, heightMeasureSpec)
        updateContentPadding(bar.measuredHeight)
        fade.layoutParams.height = (bar.measuredHeight + fade.fadeHeightPx)
            .coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
