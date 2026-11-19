package com.axoo.features.home.presentation.components

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import android.widget.TextView
import androidx.viewpager2.widget.ViewPager2
import com.axoo.ui.components.NativeSortTabStrip
import com.axoo.ui.theme.AppFontFamily
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.applyAxooTypography
import com.axoo.ui.theme.dp

@SuppressLint("ViewConstructor")
internal class NativeHomeAppBarView(context: Context) : FrameLayout(context) {
    private var statusTopInset = 0
    private val logo = TextView(context).apply {
        text = "Axoo"
        textSize = 28f
        applyAxooTypography(
            family = AppFontFamily.BALOO2,
            weight = 800,
        )
        setTextColor(NativeColors.accent)
        includeFontPadding = false
        scaleX = 1.06f
    }
    private val tabs = NativeSortTabStrip(context)

    init {
        setBackgroundColor(NativeColors.card)
        elevation = dp(3).toFloat()
        
        addView(logo, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(tabs, LayoutParams(dp(154), dp(48)))
    }

    fun attachTo(pager: ViewPager2) = tabs.attachTo(pager)

    fun release() = tabs.release()

    fun bind(selectedTab: Int, newFeedUnreadCount: Int) {
        tabs.bind(selectedTab, newFeedUnreadCount)
    }

    fun setStatusTopInset(inset: Int) {
        if (statusTopInset == inset) return
        statusTopInset = inset
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val contentHeight = maxOf(dp(48), logo.lineHeight)
        val height = contentHeight + statusTopInset
        logo.measure(
            MeasureSpec.makeMeasureSpec(width / 2, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.AT_MOST),
        )
        tabs.measure(
            MeasureSpec.makeMeasureSpec(dp(154), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val height = bottom - top
        val contentTop = statusTopInset
        val contentHeight = height - statusTopInset
        val padH = dp(16)
        val logoTop = contentTop + (contentHeight - logo.measuredHeight) / 2
        logo.layout(padH, logoTop, padH + logo.measuredWidth, logoTop + logo.measuredHeight)

        val tabsLeft = right - left - padH - tabs.measuredWidth
        val tabsTop = contentTop + (contentHeight - tabs.measuredHeight) / 2
        tabs.layout(tabsLeft, tabsTop, tabsLeft + tabs.measuredWidth, tabsTop + tabs.measuredHeight)
    }
}
