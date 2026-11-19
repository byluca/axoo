package com.axoo.features.profile.presentation.components

import android.content.Context
import android.widget.FrameLayout
import com.axoo.application.navigation.components.NativeBottomNavigationBar
import com.axoo.application.navigation.route.BottomTab

internal class NativeProfileBottomBar(context: Context) : FrameLayout(context) {
    private val nav = NativeBottomNavigationBar(context)

    init {
        clipChildren = false
        clipToPadding = false
        addView(nav, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setOnTabSelected(callback: (BottomTab) -> Unit) {
        nav.setOnTabSelected(callback)
    }

    fun bindUnreadCount(count: Int) = nav.bindUnreadCount(count)

    fun bind(tab: BottomTab) {
        nav.bind(currentTab = tab)
    }

    fun setNavigationBottomInset(inset: Int) {
        nav.setNavigationBottomInset(inset)
    }
}
