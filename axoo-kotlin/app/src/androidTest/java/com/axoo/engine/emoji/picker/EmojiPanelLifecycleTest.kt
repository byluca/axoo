package com.axoo.engine.emoji.picker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.application.bootstrap.MainActivity
import com.axoo.engine.emoji.catalog.EmojiData
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.ui.theme.dp
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmojiPanelLifecycleTest {
    @Test fun categoriesRemainStaticWhenPickerClosesAndReopens() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var panel: EmojiPanelView
        lateinit var category: View
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                EmojiData.configure(activity)
                panel = EmojiPanelView(activity, EmojiData.categories, hasBackspace = true)
                activity.setContentView(FrameLayout(activity).apply {
                    clipChildren = false
                    addView(panel.getView(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(300)))
                })
                panel.setEmojiPlaybackGate(EmojiPlaybackGate.FullyVisible)
            }
            instrumentation.waitForIdleSync()
            val initial = AtomicInteger()
            instrumentation.runOnMainSync {
                category = descendants(panel.getView() as ViewGroup).filterIsInstance<EmojiCategoryButton>().first()
                initial.set(frameHash(category))
                panel.setActive(true)
            }
            SystemClock.sleep(250)
            instrumentation.runOnMainSync {
                assertEquals("Visible categories must remain static", initial.get(), frameHash(category))
            }
            instrumentation.runOnMainSync { panel.setActive(false) }
            SystemClock.sleep(150)
            val paused = AtomicInteger()
            instrumentation.runOnMainSync { paused.set(frameHash(category)) }
            SystemClock.sleep(250)
            instrumentation.runOnMainSync {
                assertEquals("Hidden picker must stop category animation", paused.get(), frameHash(category))
                panel.setActive(true)
            }
            SystemClock.sleep(250)
            instrumentation.runOnMainSync {
                assertEquals("Reopening must preserve the static icon", initial.get(), frameHash(category))
            }
            instrumentation.runOnMainSync { panel.setActive(false); panel.dispose() }
        }
    }

    @Test fun scrollingHidesAllControlsAndUpwardScrollRestoresTheCompactMediaPill() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var panel: EmojiPanelView
        lateinit var grid: androidx.recyclerview.widget.RecyclerView
        lateinit var delete: EmojiBackspaceButton
        lateinit var pill: com.axoo.ui.components.TextCategoryBarView
        val selected = mutableListOf<Boolean>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                EmojiData.configure(activity)
                panel = EmojiPanelView(activity, EmojiData.categories, hasBackspace = true, onMediaRequested = selected::add)
                activity.setContentView(FrameLayout(activity).apply {
                    addView(panel.getView(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(300)))
                })
                panel.setActive(true)
                val views = descendants(panel.getView() as ViewGroup)
                grid = views.filterIsInstance<androidx.recyclerview.widget.RecyclerView>().first()
                delete = views.filterIsInstance<EmojiBackspaceButton>().first()
                pill = views.filterIsInstance<com.axoo.ui.components.TextCategoryBarView>().first()
            }
            instrumentation.waitForIdleSync()
            fun drag(down: Boolean) = instrumentation.runOnMainSync {
                val x = grid.width / 2f
                val from = grid.height * if (down) 0.75f else 0.30f
                val to = grid.height * if (down) 0.30f else 0.75f
                val start = SystemClock.uptimeMillis()
                for (step in 0..8) {
                    val action = when (step) { 0 -> android.view.MotionEvent.ACTION_DOWN; 8 -> android.view.MotionEvent.ACTION_UP; else -> android.view.MotionEvent.ACTION_MOVE }
                    val event = android.view.MotionEvent.obtain(start, start + step * 30, action, x, from + (to - from) * step / 8f, 0)
                    grid.dispatchTouchEvent(event)
                    event.recycle()
                }
                grid.stopScroll()
            }
            instrumentation.runOnMainSync {
                val root = panel.getView() as ViewGroup
                val pillRect = android.graphics.Rect()
                val deleteRect = android.graphics.Rect()
                pill.getDrawingRect(pillRect); root.offsetDescendantRectToMyCoords(pill, pillRect)
                delete.getDrawingRect(deleteRect); root.offsetDescendantRectToMyCoords(delete, deleteRect)
                assertTrue(pillRect.right < deleteRect.left)
            }
            drag(true)
            SystemClock.sleep(220)
            instrumentation.runOnMainSync { assertEquals(View.INVISIBLE, delete.visibility); assertEquals(View.INVISIBLE, pill.visibility) }
            drag(false)
            SystemClock.sleep(220)
            instrumentation.runOnMainSync {
                assertEquals(View.VISIBLE, delete.visibility)
                assertEquals(View.VISIBLE, pill.visibility)
                assertTrue(pill.isEnabled)
                assertTrue(delete.isEnabled)
                val labels = descendants(pill).filterIsInstance<android.widget.TextView>()
                assertEquals(listOf("Emoji", "GIF", "Sticker"), labels.map { it.text.toString() })
                labels[1].performClick(); labels[2].performClick()
                assertEquals(listOf(false, true), selected)
                panel.dispose()
            }
        }
    }

    private fun frameHash(view: View): Int {
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        return try {
            view.draw(Canvas(bitmap))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            pixels.contentHashCode()
        } finally { bitmap.recycle() }
    }
    private fun descendants(group: ViewGroup): List<View> = (0 until group.childCount).flatMap { index ->
        val child = group.getChildAt(index)
        listOf(child) + if (child is ViewGroup) descendants(child) else emptyList()
    }
}
