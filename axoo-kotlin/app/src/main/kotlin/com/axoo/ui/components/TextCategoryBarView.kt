package com.axoo.ui.components

import android.content.Context
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp

internal data class TextCategoryItem(val id: String, val label: String)

/** Text categories with the shared sliding indicator and horizontal scrolling. */
internal class TextCategoryBarView(context: Context, private val fitWidth: Boolean = false) : HorizontalScrollView(context) {
    private val row = SlidingCategoryIndicatorLayout(
        context = context,
        indicatorWidthPx = 0,
        indicatorHeightPx = context.dp(if (fitWidth) 26 else 36),
        indicatorCornerRadiusPx = context.dp(if (fitWidth) 13 else 8).toFloat(),
    ).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        if (fitWidth) setPadding(context.dp(5), 0, context.dp(5), 0)
    }
    private var items: List<TextCategoryItem> = emptyList()
    private var selectedId: String? = null
    private var onSelected: (String?) -> Unit = {}

    init {
        isFillViewport = fitWidth
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(row, LayoutParams(if (fitWidth) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    fun bind(items: List<TextCategoryItem>, selectedId: String?, onSelected: (String?) -> Unit) {
        this.onSelected = onSelected
        val itemsChanged = this.items != items
        val selectionChanged = this.selectedId != selectedId
        if (!itemsChanged && !selectionChanged) return
        val animate = !itemsChanged && selectionChanged
        this.items = items
        this.selectedId = selectedId
        if (itemsChanged) {
            row.removeAllViews()
            items.forEach { item ->
                row.addView(TextView(context).apply {
                    text = item.label
                    textSize = if (fitWidth) 13f else 15f
                    typeface = context.baloo2Typeface(700)
                    includeFontPadding = false
                    gravity = Gravity.CENTER
                    setSingleLine(true)
                    minimumWidth = context.dp(44)
                    setPadding(context.dp(if (fitWidth) 2 else 10), 0, context.dp(if (fitWidth) 2 else 10), 0)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { this@TextCategoryBarView.onSelected(item.id) }
                }, LinearLayout.LayoutParams(if (fitWidth) 0 else LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, if (fitWidth) 1f else 0f))
            }
        }
        items.forEachIndexed { index, item ->
            (row.getChildAt(index) as TextView).apply {
                isSelected = item.id == selectedId
                setTextColor(if (isSelected) NativeColors.accent else NativeColors.textPrimary)
            }
        }
        val selectedIndex = items.indexOfFirst { it.id == selectedId }
        row.setSelectedIndex(selectedIndex, animate = animate)
        if (selectedIndex >= 0) {
            post {
                row.getChildAt(this.items.indexOfFirst { it.id == this.selectedId })?.let { button ->
                    smoothScrollTo((button.left + button.width / 2 - width / 2).coerceAtLeast(0), 0)
                }
            }
        }
    }
}
