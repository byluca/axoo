package com.axoo.features.posts.presentation.posteditor.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import com.axoo.features.posts.presentation.components.flairAppearance
import com.axoo.features.posts.presentation.components.flairDisplayLabel
import com.axoo.ui.components.applyNativePillSurface
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp

@SuppressLint("ViewConstructor")
internal class NativeFlairSelectorChipView(context: Context) : LinearLayout(context) {
    private val label = TextView(context).apply {
        textSize = PostEditorStyle.categoryTextSize
        typeface = context.baloo2Typeface(700)
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
    }

    private val icon = ImageView(context).apply {
        visibility = GONE
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        setPadding(dp(10), dp(4), dp(8), dp(4))

        addView(icon, LayoutParams(dp(14), dp(14)).apply { rightMargin = dp(4) })
        addView(label, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun bind(selectedFlair: String?) {
        val hasSelection = selectedFlair != null
        val text = selectedFlair ?: "Categoria"
        val appearance = flairAppearance(text)
        val bgColor = if (hasSelection) appearance.background else NativeColors.pickerControl
        val textColor = if (hasSelection) appearance.foreground else NativeColors.textSecondary

        label.text = flairDisplayLabel(text)
        label.setTextColor(textColor)
        val iconName = appearance.iconName
        icon.visibility = if (hasSelection && iconName != null) VISIBLE else GONE
        if (hasSelection && iconName != null) {
            icon.imageTintList = ColorStateList.valueOf(textColor)
            icon.load("file:///android_asset/icons/$iconName.svg")
        } else {
            icon.setImageDrawable(null)
        }
        applyNativePillSurface(
            cornerRadiusDp = 14f,
            backgroundColor = bgColor,
            borderColor = if (hasSelection) null else NativeColors.border,
        )
        elevation = 0f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, maxOf(measuredHeight, dp(26)))
    }
}
