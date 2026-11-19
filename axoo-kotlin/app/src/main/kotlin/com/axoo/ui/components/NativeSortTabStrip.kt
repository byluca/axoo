package com.axoo.ui.components

import android.graphics.Canvas
import android.graphics.Rect
import android.widget.TextView
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.view.ContextThemeWrapper
import androidx.viewpager2.widget.ViewPager2
import androidx.core.view.children
import com.axoo.R
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/** Standard tabs synchronized with the existing pager by Material. */
class NativeSortTabStrip(context: Context) : TabLayout(ContextThemeWrapper(context, R.style.Theme_Axoo_Controls)) {
    private val unreadBadge = UnreadCountBadgeRenderer(context)
    private var unreadCount = 0
    private var mediator: TabLayoutMediator? = null

    init {
        tabMode = MODE_FIXED
        tabGravity = GRAVITY_FILL
        setBackgroundColor(Color.TRANSPARENT)
        setTabTextColors(NativeColors.textSecondary, NativeColors.accent)
        setSelectedTabIndicatorColor(Color.TRANSPARENT)
        setSelectedTabIndicator(ColorDrawable(Color.TRANSPARENT))
        tabRippleColor = null
        addTab(newTab().setText("Hot"))
        addTab(newTab().setText("New"))
    }

    fun attachTo(pager: ViewPager2) {
        mediator?.detach()
        mediator = TabLayoutMediator(this, pager) { tab, index ->
            tab.text = if (index == 0) "Hot" else "New"
        }.also { it.attach() }
    }

    fun release() { mediator?.detach(); mediator = null }

    fun bind(selectedTab: Int, newUnreadCount: Int = 0) {
        val index = selectedTab.coerceIn(0, 1)
        if (selectedTabPosition != index) getTabAt(index)?.select()
        unreadCount = newUnreadCount.coerceAtLeast(0)
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (unreadCount <= 0) return
        val label = getTabAt(1)?.view?.children?.filterIsInstance<TextView>()?.firstOrNull() ?: return
        val bounds = Rect()
        label.getDrawingRect(bounds)
        offsetDescendantRectToMyCoords(label, bounds)
        val right = bounds.exactCenterX() + label.paint.measureText(label.text.toString()) / 2f
        unreadBadge.draw(canvas, unreadCount,
            minOf(width - paddingRight - unreadBadge.widthPx(unreadCount), right - context.dp(3f)),
            bounds.exactCenterY() - unreadBadge.heightPx)
    }
}
