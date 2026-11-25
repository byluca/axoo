package com.axoo.application.navigation.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Rect
import android.widget.ImageView
import com.axoo.ui.components.UnreadCountBadgeRenderer
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.ViewCompat
import coil.target.Target
import com.axoo.R
import com.axoo.application.navigation.route.Tab
import com.axoo.ui.components.SlidingCategoryIndicatorLayout
import com.axoo.ui.components.applyNativePillSurface
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView

/** Material owns the tabs; the shared indicator draws selection inside the capsule. */
internal class NativeBottomNavigationBar(context: Context) : FrameLayout(context) {
    private var onTabSelected: (Tab) -> Unit = {}
    private var destinationId = HOME
    private var unreadCount = 0
    private val badge = UnreadCountBadgeRenderer(context)
    private val navigation: BottomNavigationView = BottomNavigationView(ContextThemeWrapper(context, R.style.Theme_Axoo_Controls)).apply {
        backgroundTintList = null
        setBackgroundColor(Color.TRANSPARENT)
        elevation = 0f
        setPadding(dp(CONTENT_INSET_DP), 0, dp(CONTENT_INSET_DP), 0)
        itemIconSize = dp(24)
        itemPaddingTop = dp(3)
        itemPaddingBottom = dp(3)
        itemGravity = NavigationBarView.ITEM_GRAVITY_CENTER
        itemIconGravity = NavigationBarView.ITEM_ICON_GRAVITY_TOP
        labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
        isItemHorizontalTranslationEnabled = false
        setActiveIndicatorLabelPadding(dp(2))
        minimumHeight = dp(BAR_HEIGHT_DP)
        itemIconTintList = colors()
        itemTextColor = colors()
        setItemTextAppearanceActive(R.style.TextAppearance_Axoo_Navigation)
        setItemTextAppearanceInactive(R.style.TextAppearance_Axoo_Navigation)
        setItemTextAppearanceActiveBoldEnabled(false)
        isItemActiveIndicatorEnabled = false
        // The host already supplies the navigation-bar inset.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets -> insets }
        menu.add(0, HOME, 0, "Home")
        menu.add(0, CREATE, 1, "Crea").isCheckable = false
        menu.add(0, NOTIFICATIONS, 2, "Notifiche")
        menu.add(0, PROFILE, 3, "Profilo")
        menu.setGroupCheckable(0, true, true)
        menu.findItem(CREATE).isCheckable = false
        // Keep Create as an action: it never replaces the selected destination.
        setOnItemSelectedListener { item ->
            if (item.itemId != CREATE) {
                item.isChecked = true
                indicator.setSelectedIndex(selectionIndex(item.itemId), animate = false)
            }
            onTabSelected(tab(item.itemId))
            item.itemId != CREATE
        }
        setOnItemReselectedListener { item ->
            onTabSelected(tab(item.itemId))
        }
    }
    private val indicator: SlidingCategoryIndicatorLayout = SlidingCategoryIndicatorLayout(
        context = context,
        indicatorWidthPx = 0,
        indicatorHeightPx = dp(BAR_HEIGHT_DP - INDICATOR_INSET_DP * 2),
        indicatorCornerRadiusPx = dp((BAR_HEIGHT_DP - INDICATOR_INSET_DP * 2) / 2f),
        selectionContainer = { navigation.getChildAt(0) as? ViewGroup },
        indicatorHorizontalOutsetPx = dp(CONTENT_INSET_DP - INDICATOR_INSET_DP),
    ).apply {
        setIndicatorColor(NativeColors.navigationSelection)
        applyNativePillSurface(cornerRadiusDp = BAR_HEIGHT_DP / 2f)
        clipToOutline = true
        addView(navigation, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        navigation.menu.findItem(destinationId).isChecked = true
        indicator.setSelectedIndex(selectionIndex(destinationId), animate = false)
    }

    init {
        clipChildren = false
        clipToPadding = false
        setNavigationBottomInset(0)
        addView(indicator, LayoutParams(dp(BAR_WIDTH_DP), dp(BAR_HEIGHT_DP), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        indicator.setSelectedIndex(0, animate = false)
        loadIcon(HOME, "home.svg", "home-fill.svg")
        loadIcon(CREATE, "plus.svg")
        loadIcon(NOTIFICATIONS, "bell.svg", "bell-fill.svg")
        loadIcon(PROFILE, "user.svg", "user-filled.svg")
    }

    private fun loadIcon(id: Int, outline: String, filled: String? = null) {
        val assets = listOfNotNull(filled, outline)
        val icons = mutableMapOf<String, Drawable>()
        assets.forEach { asset ->
            coil.Coil.imageLoader(context).enqueue(coil.request.ImageRequest.Builder(context)
                .data("file:///android_asset/icons/$asset")
                .size(dp(24))
                .target(object : Target {
                    override fun onSuccess(result: Drawable) {
                        icons[asset] = result
                        if (icons.size != assets.size) return
                        // Material applies the checked state and tint, even if loading finishes after selection.
                        navigation.menu.findItem(id).icon = filled?.let { selected ->
                            StateListDrawable().apply {
                                addState(intArrayOf(android.R.attr.state_checked), icons.getValue(selected))
                                addState(intArrayOf(), icons.getValue(outline))
                            }
                        } ?: result
                    }
                }).build())
        }
    }

    fun setOnTabSelected(callback: (Tab) -> Unit) { onTabSelected = callback }

    fun bind(currentTab: Tab) {
        destinationId = when (currentTab) {
            Tab.Home -> HOME
            Tab.Notifications -> NOTIFICATIONS
            Tab.Profile -> PROFILE
            Tab.Auth, Tab.Create -> return
        }
        navigation.menu.findItem(destinationId).isChecked = true
        indicator.setSelectedIndex(selectionIndex(destinationId), animate = false)
    }

    fun setNavigationBottomInset(inset: Int) {
        setPadding(dp(HORIZONTAL_INSET_DP), dp(8), dp(HORIZONTAL_INSET_DP), dp(8) + inset.coerceAtLeast(0))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Keep the photographed iOS width; only shrink on narrow windows.
        val availableWidth = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            dp(BAR_WIDTH_DP)
        } else {
            (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        }
        indicator.layoutParams.width = minOf(dp(BAR_WIDTH_DP), availableWidth)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun bindUnreadCount(count: Int) {
        unreadCount = count.coerceAtLeast(0)
        androidx.core.view.MenuItemCompat.setContentDescription(navigation.menu.findItem(NOTIFICATIONS), if (unreadCount > 0) "Notifiche, $unreadCount nuove" else "Notifiche")
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (unreadCount <= 0) return
        val item = navigation.findViewById<ViewGroup>(NOTIFICATIONS) ?: return
        val icon = item.findViewById<ImageView>(com.google.android.material.R.id.navigation_bar_item_icon_view) ?: return
        val rect = Rect()
        icon.getDrawingRect(rect)
        offsetDescendantRectToMyCoords(icon, rect)
        badge.draw(canvas, unreadCount, minOf(width - badge.widthPx(unreadCount), rect.right - dp(3f)),
            rect.exactCenterY() - badge.heightPx)
    }

    private fun selectionIndex(id: Int): Int = navigation.menu.findItem(id).order

    private fun colors() = ColorStateList.valueOf(NativeColors.textPrimary)

    private fun tab(id: Int) = when (id) {
        HOME -> Tab.Home
        CREATE -> Tab.Create
        NOTIFICATIONS -> Tab.Notifications
        else -> Tab.Profile
    }

    private companion object {
        // Approximate point measurements from photo_2026-10-04_21-58-20.jpg.
        const val BAR_WIDTH_DP = 352
        const val BAR_HEIGHT_DP = 62
        const val CONTENT_INSET_DP = 8
        const val INDICATOR_INSET_DP = 5
        const val HORIZONTAL_INSET_DP = 16
        const val HOME = 1
        const val CREATE = 2
        const val PROFILE = 3
        const val NOTIFICATIONS = 4
    }
}
