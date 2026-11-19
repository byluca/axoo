package com.axoo.engine.emoji.picker

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.axoo.ui.components.SlidingCategoryIndicatorLayout

internal class EmojiCategoryBar(
    private val context: Context,
    private val onCategoryClicked: (Int) -> Unit,
) {
    data class CategoryItem(
        val index: Int,
        val id: String,
        val label: String,
    )

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density).toInt()

    private val barContainer = SlidingCategoryIndicatorLayout(
        context = context,
        indicatorWidthPx = dp(36),
        indicatorHeightPx = dp(44),
        indicatorCornerRadiusPx = dp(22).toFloat(),
    ).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
    }

    private val barScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(
            barContainer,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private val categoryButtons = mutableListOf<EmojiCategoryButton>()
    private var activeCategoryIndex = 0

    fun getView(): HorizontalScrollView = barScroll

    fun setCategories(items: List<CategoryItem>) {
        barContainer.removeAllViews()
        categoryButtons.clear()

        for (item in items) {
            addCategoryButton(item)
        }
        updateCategorySelection(animate = false)
        ensureVisible()
    }

    fun setActiveCategoryIndex(index: Int) {
        if (activeCategoryIndex == index) return
        activeCategoryIndex = index
        updateCategorySelection(animate = true)
    }

    fun ensureVisible() {
        barScroll.animate().cancel()
        barScroll.visibility = View.VISIBLE
        barScroll.alpha = 1f
        barScroll.translationY = 0f
        barScroll.bringToFront()
    }

    fun dispose() {
        barScroll.animate().cancel()
        categoryButtons.clear()
    }

    private fun addCategoryButton(item: CategoryItem) {
        val button = EmojiCategoryButton(context).apply {
            bind(item.id)
            contentDescription = item.label
            setOnClickListener {
                onCategoryClicked(item.index)
            }
        }
        categoryButtons.add(button)
        val params = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            leftMargin = dp(1)
            rightMargin = dp(1)
        }
        barContainer.addView(button, params)
    }

    private fun updateCategorySelection(animate: Boolean) {
        for (i in categoryButtons.indices) {
            val categoryIndex = i
            val selected = categoryIndex == activeCategoryIndex
            categoryButtons[i].setSelectedState(selected)
        }
        val buttonIndex = activeCategoryIndex
        barContainer.setSelectedIndex(buttonIndex, animate = animate)
        ensureActiveCategoryVisible()
    }

    private fun ensureActiveCategoryVisible() {
        barScroll.post {
            val button = categoryButtons.getOrNull(activeCategoryIndex) ?: return@post
            val target = if (activeCategoryIndex == 0) 0 else
                (button.left + button.width / 2 - barScroll.width / 2).coerceAtLeast(0)
            barScroll.scrollTo(target, 0)
        }
    }
}
