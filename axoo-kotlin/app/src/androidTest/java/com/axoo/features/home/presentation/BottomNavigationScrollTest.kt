package com.axoo.features.home.presentation

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.application.bootstrap.MainActivity
import com.axoo.application.navigation.components.NativeBottomNavigationBar
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.engine.emoji.list.EmojiRecyclerView
import com.axoo.features.home.presentation.components.NativeHomeRootView
import com.axoo.features.posts.domain.Post
import com.axoo.ui.theme.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomNavigationScrollTest {
    @Test
    fun barStaysVisibleWhileDraggingInBothDirectionsWithoutMovingTheFeedLayout() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var root: NativeHomeRootView
            lateinit var feed: EmojiRecyclerView
            lateinit var bar: NativeBottomNavigationBar
            scenario.onActivity { activity ->
                root = NativeHomeRootView(activity)
                activity.addContentView(root, ViewGroup.LayoutParams(-1, -1))
                root.setScreenPlaybackGate(EmojiPlaybackGate.FullyVisible)
                root.render(HomeUiState(hotFeed = HomeFeedUiState(posts = List(30) { index ->
                    Post(
                        id = "navigation-test-$index", userId = "navigation-test-author",
                        universityId = "test", nickname = "Test", universityName = "Campus",
                        body = "Un post del feed per verificare la navigation bar durante lo scroll.",
                        createdAt = "2026-10-03T12:00:00Z",
                    )
                })))
            }
            try {
                feed = awaitVisibleFeed(root)
                var originalPadding = 0
                var originalHeight = 0
                onMain {
                    bar = descendants(root).filterIsInstance<NativeBottomNavigationBar>().single()
                    originalPadding = feed.paddingBottom
                    originalHeight = feed.height
                    drag(feed, root.context.dp(160))
                }
                assertBarStaysVisible(bar)
                onMain {
                    assertEquals(originalPadding, feed.paddingBottom)
                    assertEquals(originalHeight, feed.height)
                    drag(feed, -root.context.dp(100))
                }
                assertBarStaysVisible(bar)
                onMain {
                    assertTrue("The feed must still be below the top", feed.canScrollVertically(-1))
                    assertEquals(originalPadding, feed.paddingBottom)
                    assertEquals(originalHeight, feed.height)
                }
            } finally {
                onMain { root.release() }
            }
        }
    }

    private fun awaitVisibleFeed(root: View): EmojiRecyclerView {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            var feed: EmojiRecyclerView? = null
            onMain {
                feed = descendants(root).filterIsInstance<EmojiRecyclerView>()
                    .singleOrNull { it.childCount > 0 && (it.adapter?.itemCount ?: 0) > 0 && it.getGlobalVisibleRect(Rect()) }
            }
            feed?.let { return it }
            SystemClock.sleep(16)
        }
        error("Feed did not draw")
    }

    private fun drag(feed: View, distance: Int) {
        val start = SystemClock.uptimeMillis()
        val x = feed.width / 2f
        val y = feed.height / 2f
        fun dispatch(action: Int, elapsed: Long, position: Float) {
            val event = MotionEvent.obtain(start, start + elapsed, action, x, position, 0)
            try { feed.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        dispatch(MotionEvent.ACTION_DOWN, 0, y)
        for (step in 1..6) dispatch(MotionEvent.ACTION_MOVE, step * 16L, y - distance * step / 6f)
        // Cancel ends dragging without adding an unrelated fling to the test.
        dispatch(MotionEvent.ACTION_CANCEL, 112, y - distance)
    }

    private fun assertBarStaysVisible(bar: View) {
        // Observe several frames so a delayed hide animation would fail the test.
        repeat(20) {
            onMain {
                assertEquals(View.VISIBLE, bar.visibility)
                assertEquals(1f, bar.alpha)
                assertEquals(0f, bar.translationY)
            }
            SystemClock.sleep(16)
        }
    }

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) repeat(view.childCount) { yieldAll(descendants(view.getChildAt(it))) }
    }
}
