package com.axoo.application.navigation.components

import android.graphics.Bitmap
import android.graphics.Canvas
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp
import org.junit.Assert.assertTrue
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.axoo.application.navigation.route.Tab
import com.axoo.ui.components.NativeSortTabStrip
import com.axoo.ui.components.SlidingCategoryIndicatorLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeNavigationControlsTest {
    @Test
    fun createKeepsTheOriginSelectedAndBindingDoesNotNavigate() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bar = NativeBottomNavigationBar(context)
        val capsule = bar.getChildAt(0) as SlidingCategoryIndicatorLayout
        val navigation = capsule.getChildAt(0) as BottomNavigationView
        val events = mutableListOf<Tab>()
        bar.setOnTabSelected(events::add)
        assertEquals(listOf("Home", "Crea", "Notifiche", "Profilo"), (0 until navigation.menu.size()).map { navigation.menu.getItem(it).title.toString() })
        val create = navigation.menu.getItem(1).itemId
        for (origin in listOf(Tab.Home, Tab.Notifications, Tab.Profile)) {
            bar.bind(origin)
            assertEquals(emptyList<Tab>(), events)
            val selected = navigation.selectedItemId
            navigation.selectedItemId = create
            assertEquals(listOf(Tab.Create), events)
            assertEquals(selected, navigation.selectedItemId)
            events.clear()
        }
    }

    @Test
    fun notificationsSelectOnFirstBindAndFirstTapFromEveryDestination() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bar = NativeBottomNavigationBar(context)
        val capsule = bar.getChildAt(0) as SlidingCategoryIndicatorLayout
        val navigation = capsule.getChildAt(0) as BottomNavigationView
        val notificationId = navigation.menu.getItem(2).itemId
        for (origin in listOf(Tab.Home, Tab.Profile, Tab.Notifications, Tab.Home)) {
            bar.bind(origin)
            navigation.selectedItemId = notificationId
            bar.measure(View.MeasureSpec.makeMeasureSpec(context.dp(390), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(context.dp(100), View.MeasureSpec.AT_MOST))
            bar.layout(0, 0, bar.measuredWidth, bar.measuredHeight)
            assertEquals(notificationId, navigation.selectedItemId)
            assertTrue(navigation.menu.findItem(notificationId).isChecked)
            assertEquals(1, (0 until navigation.menu.size()).count { navigation.menu.getItem(it).isChecked })
            val bitmap = Bitmap.createBitmap(capsule.width, capsule.height, Bitmap.Config.ARGB_8888)
            capsule.draw(Canvas(bitmap))
            val item = navigation.findViewById<View>(notificationId)
            val rect = android.graphics.Rect()
            item.getDrawingRect(rect)
            capsule.offsetDescendantRectToMyCoords(item, rect)
            assertEquals(NativeColors.navigationSelection, bitmap.getPixel(rect.centerX(), context.dp(6)))
            bitmap.recycle()
        }
        // A screen binds its destination before attachment and before SVG loading finishes.
        val next = NativeBottomNavigationBar(context)
        next.bind(Tab.Notifications)
        val native = (next.getChildAt(0) as ViewGroup).getChildAt(0) as BottomNavigationView
        assertEquals(notificationId, native.selectedItemId)
        assertTrue(native.menu.findItem(notificationId).isChecked)
    }

    @Test
    fun cachedDestinationsRestoreTheirSelectionAfterReattachment() {
        androidx.test.core.app.ActivityScenario.launch(com.axoo.application.bootstrap.MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = android.widget.FrameLayout(activity)
                activity.setContentView(root)
                val bars = listOf(Tab.Home, Tab.Notifications, Tab.Profile).associateWith { tab ->
                    NativeBottomNavigationBar(activity).apply { bind(tab) }
                }
                fun native(bar: NativeBottomNavigationBar) = (bar.getChildAt(0) as ViewGroup).getChildAt(0) as BottomNavigationView
                fun show(tab: Tab) { root.removeAllViews(); root.addView(bars.getValue(tab)) }
                bars.values.forEach { bar -> bar.setOnTabSelected { if (it != Tab.Create) show(it) } }
                show(Tab.Home)
                for (tab in listOf(Tab.Notifications, Tab.Home, Tab.Profile, Tab.Notifications, Tab.Home)) {
                    val index = when (tab) { Tab.Home -> 0; Tab.Notifications -> 2; else -> 3 }
                    val current = native(root.getChildAt(0) as NativeBottomNavigationBar)
                    current.selectedItemId = current.menu.getItem(index).itemId
                    val visible = native(root.getChildAt(0) as NativeBottomNavigationBar)
                    assertEquals(visible.menu.getItem(index).itemId, visible.selectedItemId)
                    assertEquals(1, (0 until visible.menu.size()).count { visible.menu.getItem(it).isChecked })
                }
            }
        }
    }

    @Test
    fun nativeTabsFollowThePagerAndTabSelectionChangesThePager() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pager = ViewPager2(context).apply {
            adapter = object : RecyclerView.Adapter<Holder>() {
                override fun getItemCount() = 2
                override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(View(parent.context))
                override fun onBindViewHolder(holder: Holder, position: Int) = Unit
            }
        }
        val tabs = NativeSortTabStrip(context)
        tabs.attachTo(pager)
        try {
            pager.setCurrentItem(1, false)
            assertEquals(1, tabs.selectedTabPosition)
            tabs.getTabAt(0)!!.select()
            assertEquals(0, pager.currentItem)
            tabs.measure(View.MeasureSpec.makeMeasureSpec(context.dp(154), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(context.dp(48), View.MeasureSpec.EXACTLY))
            tabs.layout(0, 0, tabs.measuredWidth, tabs.measuredHeight)
            fun redPixels(): Int {
                val bitmap = Bitmap.createBitmap(tabs.width, tabs.height, Bitmap.Config.ARGB_8888)
                tabs.draw(Canvas(bitmap))
                var count = 0
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                    if (bitmap.getPixel(x, y) == NativeColors.destructive) count++
                }
                bitmap.recycle()
                return count
            }
            tabs.bind(0, 3)
            val single = redPixels()
            assertTrue(single > 0)
            assertEquals(null, tabs.getTabAt(1)!!.badge)
            tabs.bind(0, 100)
            assertTrue(redPixels() > single)
            tabs.bind(0, 0)
            assertEquals(0, redPixels())
        } finally { tabs.release() }
    }

    private class Holder(view: View) : RecyclerView.ViewHolder(view)
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
