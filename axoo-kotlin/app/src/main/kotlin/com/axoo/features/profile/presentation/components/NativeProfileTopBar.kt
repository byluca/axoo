package com.axoo.features.profile.presentation.components

import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import coil.load
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp

internal class NativeProfileTopBar(context: Context) : FrameLayout(context) {
    private var statusTopInset = 0
    private val title = TextView(context).apply {
        text = "Profilo"
        textSize = 22f
        typeface = context.baloo2Typeface(800)
        includeFontPadding = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val menu = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(10), dp(10), dp(10), dp(10))
        isClickable = true
        isFocusable = true
        load("file:///android_asset/icons/hamburger-menu.svg")
    }
    private var onMenu: () -> Unit = {}

    init {
        setBackgroundColor(NativeColors.card)
        addView(title)
        addView(menu)
        menu.setOnClickListener { onMenu() }
    }

    fun setOnMenu(callback: () -> Unit) {
        onMenu = callback
    }

    fun render() {
        setBackgroundColor(NativeColors.card)
        title.setTextColor(NativeColors.textPrimary)
        menu.imageTintList = ColorStateList.valueOf(NativeColors.textPrimary)
    }

    fun setStatusTopInset(inset: Int) {
        if (statusTopInset == inset) return
        statusTopInset = inset
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val contentHeight = dp(44)
        title.measure(
            MeasureSpec.makeMeasureSpec((width - dp(76)).coerceAtLeast(0), MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.AT_MOST),
        )
        menu.measure(
            MeasureSpec.makeMeasureSpec(dp(44), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(44), MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(width, statusTopInset + contentHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val contentTop = statusTopInset
        val contentHeight = height - statusTopInset
        val titleTop = contentTop + (contentHeight - title.measuredHeight) / 2
        val titleLeft = dp(16)
        title.layout(
            titleLeft,
            titleTop,
            titleLeft + title.measuredWidth,
            titleTop + title.measuredHeight,
        )
        val menuTop = contentTop + (contentHeight - menu.measuredHeight) / 2
        menu.layout(width - dp(52), menuTop, width - dp(8), menuTop + menu.measuredHeight)
    }
}
