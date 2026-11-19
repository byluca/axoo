package com.axoo.features.posts.presentation.media

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.axoo.application.bootstrap.MainActivity
import com.axoo.ui.components.MediaPreviewOverlay
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HoldMediaPreviewTest {
    @Test fun holdSlideReleaseCancelAndNormalTap() {
        var selections = 0
        val previews = mutableListOf<String>()
        val visibility = mutableListOf<Boolean>()
        lateinit var first: View
        lateinit var second: View
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val root = FrameLayout(activity)
                first = View(activity)
                second = View(activity)
                root.addView(first, FrameLayout.LayoutParams(200, 200).apply { topMargin = 200 })
                root.addView(second, FrameLayout.LayoutParams(200, 200).apply { topMargin = 200; leftMargin = 220 })
                listOf(first to "first", second to "second").forEach { (view, key) ->
                    view.setOnClickListener { selections++ }
                    MediaPreviewOverlay.bind(view, key, onVisibility = { visibility += it }) { context ->
                        previews += key
                        View(context)
                    }
                }
                activity.setContentView(root)
            }
            SystemClock.sleep(300)
            scenario.onActivity {
                touch(first, MotionEvent.ACTION_DOWN)
                first.performLongClick()
                assertTrue(MediaPreviewOverlay.isShowing)
                assertEquals(0, selections)
                val a = IntArray(2); first.getLocationOnScreen(a)
                val b = IntArray(2); second.getLocationOnScreen(b)
                touch(first, MotionEvent.ACTION_MOVE, (b[0] - a[0] + 40).toFloat(), (b[1] - a[1] + 40).toFloat())
                assertEquals(listOf("first", "second"), previews)
                touch(first, MotionEvent.ACTION_UP)
                assertFalse(MediaPreviewOverlay.isShowing)
                assertEquals(0, selections)
                assertEquals(listOf(true, false), visibility)
                touch(first, MotionEvent.ACTION_DOWN)
                touch(first, MotionEvent.ACTION_UP)
            }
            SystemClock.sleep(150)
            scenario.onActivity {
                assertEquals(1, selections)
                touch(first, MotionEvent.ACTION_DOWN)
                first.performLongClick()
                touch(first, MotionEvent.ACTION_CANCEL)
                assertFalse(MediaPreviewOverlay.isShowing)
                assertEquals(1, selections)
                first.performLongClick()
                (first.parent as FrameLayout).removeView(first)
                assertFalse(MediaPreviewOverlay.isShowing)
            }
        }
        assertFalse(MediaPreviewOverlay.isShowing)
    }

    private fun touch(view: View, action: Int, x: Float = 40f, y: Float = 40f) {
        val location = IntArray(2); view.getLocationOnScreen(location)
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x + location[0], y + location[1], 0)
        event.offsetLocation(-location[0].toFloat(), -location[1].toFloat())
        view.dispatchTouchEvent(event)
        event.recycle()
    }
}
