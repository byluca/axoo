package com.axoo.application.navigation.host

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.application.navigation.factory.Navigator
import com.axoo.application.navigation.factory.ScreenFactory
import com.axoo.application.navigation.route.NativeScreen
import com.axoo.application.navigation.route.NavigationSnapshot
import com.axoo.application.navigation.route.Route
import com.axoo.application.navigation.route.Tab
import com.axoo.application.navigation.route.toPersistedRoute
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationHostTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val post = Route.PostDetail("post-1")

    private class Fixture(context: Context, snapshot: NavigationSnapshot? = null) {
        val events = mutableListOf<Pair<Route, String>>()
        val screens = mutableListOf<Screen>()
        var onVisible: () -> Unit = {}
        private var downTime = 0L
        val host = NavigationHost(context, object : ScreenFactory {
            override fun create(context: Context, route: Route, navigator: Navigator): View =
                Screen(context, route) { screenRoute, event ->
                    events += screenRoute to event
                    if (event == "visible") onVisible()
                }.also(screens::add)
        }, snapshot)

        init { layout() }

        fun layout() {
            host.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY),
            )
            host.layout(0, 0, 1000, 1600)
        }

        fun screen(route: Route): Screen = screens.last { it.route == route }

        fun assertTop(route: Route) {
            assertEquals(NavigationState.Idle, host.currentNavigationState)
            assertEquals(2, host.childCount)
            val layer = host.getChildAt(host.childCount - 1) as FrameLayout
            assertEquals(1, layer.childCount)
            assertSame(screen(route), layer.getChildAt(0))
            repeat(host.childCount) {
                assertEquals(0f, host.getChildAt(it).translationX, 0f)
                assertEquals(0f, host.getChildAt(it).translationY, 0f)
                assertFalse(host.getChildAt(it).clipToOutline)
            }
        }

        fun touch(action: Int, x: Float, elapsed: Long) {
            if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(downTime, downTime + elapsed, action, x, 200f, 0)
            try {
                host.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }

        fun startSwipe() {
            layout()
            touch(MotionEvent.ACTION_DOWN, 1f, 0)
            touch(MotionEvent.ACTION_MOVE, 500f, 200)
            assertEquals(NavigationState.InteractiveSwipe, host.currentNavigationState)
        }
    }

    private class Screen(
        context: Context,
        override val route: Route,
        private val record: (Route, String) -> Unit,
    ) : View(context), NativeScreen, BackHandler {
        var consumeBack = false
        init { isClickable = true }
        override fun onBackPressed(): Boolean = consumeBack
        override fun onScreenTransitionStart(isOpening: Boolean, isBackward: Boolean) =
            record(route, "start:$isOpening:$isBackward")
        override fun onInteractiveTransitionStart(isOpening: Boolean) = record(route, "swipe:$isOpening")
        override fun onScreenFullyVisible() = record(route, "visible")
        override fun onScreenHidden() = record(route, "hidden")
        override fun onScreenDestroyed() = record(route, "destroyed")
    }

    @Test
    fun initialScreenIsVisibleAndBackDoesNotRemoveTheRoot() {
        onMain {
            val f = Fixture(instrumentation.targetContext)
            assertEquals(listOf(Route.Home to "visible"), f.events)
            assertFalse(f.host.onBackPressed())
            f.assertTop(Route.Home)
        }
    }

    @Test
    fun pushAndPopDispatchLifecycleInOrderAndReuseThePreviousScreen() {
        val f = fixture()
        transition(f) { f.host.navigate(post) }
        onMain {
            assertEquals(listOf(
                Route.Home to "start:false:false", post to "start:true:false",
                Route.Home to "hidden", post to "visible",
            ), f.events)
            f.assertTop(post)
            f.events.clear()
        }
        transition(f) { assertTrue(f.host.pop()) }
        onMain {
            assertEquals(listOf(
                post to "start:false:true", Route.Home to "start:true:true",
                post to "hidden", post to "destroyed", Route.Home to "visible",
            ), f.events)
            assertEquals(2, f.screens.size)
            assertNull(f.screen(post).parent)
            f.assertTop(Route.Home)
        }
    }

    @Test
    fun replaceDestroysOnlyTheReplacedScreenAndDoesNotAddAStackEntry() {
        val f = fixture()
        transition(f) { f.host.navigate(post) }
        onMain { f.events.clear() }
        transition(f) { f.host.replace(Route.EditPost("post-1")) }
        onMain {
            assertEquals(listOf(
                post to "start:false:false", Route.EditPost("post-1") to "start:true:false",
                post to "hidden", post to "destroyed", Route.EditPost("post-1") to "visible",
            ), f.events)
            assertEquals(2, f.host.saveState().stacks.getValue(Tab.Home).size)
            f.assertTop(Route.EditPost("post-1"))
        }
        transition(f) { f.host.pop() }
        onMain { f.assertTop(Route.Home) }
    }

    @Test
    fun switchingTabsPreservesScreensAndEachTabsStack() {
        val f = fixture()
        transition(f) { f.host.navigate(post) }
        onMain {
            f.events.clear()
            f.host.navigateTab(Tab.Profile)
            f.host.navigateTab(Tab.Home)
            f.assertTop(post)
            assertEquals(3, f.screens.size)
            assertFalse(f.events.any { it.second == "destroyed" })
            assertEquals(2, f.host.saveState().stacks.getValue(Tab.Home).size)
            f.host.navigateTab(Tab.Profile, Route.PostDetail("profile-post"))
            f.assertTop(Route.PostDetail("profile-post"))
            assertEquals(2, f.host.saveState().stacks.getValue(Tab.Profile).size)
            assertEquals(2, f.host.saveState().stacks.getValue(Tab.Home).size)
        }
    }

    @Test
    fun createTabSlidesVerticallyAndPreservesBothScreensWhenClosingAndReopening() {
        val f = fixture()
        transition(f) {
            f.host.navigateTab(Tab.Create)
            val front = f.host.getChildAt(f.host.childCount - 1)
            assertTrue(front.translationY > 0f)
            assertEquals(0f, front.translationX, 0f)
            // A second tap while opening must not create another screen.
            f.host.navigateTab(Tab.Create)
        }
        onMain { f.assertTop(Route.PostEditor) }
        transition(f) { f.host.navigateTab(Tab.Home) }
        onMain {
            f.assertTop(Route.Home)
            assertFalse(f.events.any { it.second == "destroyed" })
            assertEquals(2, f.screens.size)
        }
        transition(f) { f.host.navigateTab(Tab.Create) }
        onMain {
            f.assertTop(Route.PostEditor)
            assertEquals(2, f.screens.size)
            assertEquals(1, f.host.saveState().stacks.getValue(Tab.Create).size)
        }
    }

    @Test
    fun repeatedTabSelectionDoesNotDetachOrRestartTheCurrentScreen() {
        val f = fixture()
        onMain {
            val parent = f.screen(Route.Home).parent
            f.host.navigateTab(Tab.Home)
            assertEquals(listOf(Route.Home to "visible"), f.events)
            assertSame(parent, f.screen(Route.Home).parent)
            assertEquals(1, f.screens.size)
            f.assertTop(Route.Home)
        }
    }

    @Test
    fun screenBackHandlerCanConsumeBackWithoutChangingTheStack() {
        val f = fixture()
        transition(f) { f.host.navigate(post) }
        onMain {
            f.screen(post).consumeBack = true
            assertTrue(f.host.onBackPressed())
            assertEquals(2, f.host.saveState().stacks.getValue(Tab.Home).size)
            f.assertTop(post)
        }
    }

    @Test
    fun cancellingSwipeKeepsBothCachedScreensAndTheStack() {
        val f = fixture()
        transition(f) { f.host.navigate(post) }
        onMain {
            f.events.clear()
            f.startSwipe()
        }
        transition(f) { f.touch(MotionEvent.ACTION_CANCEL, 500f, 300) }
        onMain {
            assertEquals(listOf(
                post to "swipe:false", Route.Home to "swipe:true",
                Route.Home to "hidden", post to "visible",
            ), f.events)
            assertNotNull(f.screen(Route.Home).parent)
            assertEquals(2, f.screens.size)
            assertEquals(2, f.host.saveState().stacks.getValue(Tab.Home).size)
            f.assertTop(post)
        }
    }

    @Test
    fun completingSwipePopsOnceAndDestroysOnlyTheOutgoingScreen() {
        val f = fixture()
        transition(f) { f.host.navigate(post) }
        onMain {
            f.events.clear()
            f.startSwipe()
            // Navigation must be ignored while the finger controls the transition.
            f.host.navigate(Route.Profile)
            f.host.navigateTab(Tab.Profile)
            assertFalse(f.host.pop())
        }
        transition(f) { f.touch(MotionEvent.ACTION_UP, 500f, 300) }
        onMain {
            assertEquals(listOf(
                post to "swipe:false", Route.Home to "swipe:true",
                post to "hidden", post to "destroyed", Route.Home to "visible",
            ), f.events)
            assertEquals(1, f.host.saveState().stacks.getValue(Tab.Home).size)
            assertEquals(Tab.Home, f.host.saveState().currentTab)
            assertNull(f.screen(post).parent)
            f.assertTop(Route.Home)
        }
    }

    @Test
    fun cancellingRestoredSwipeReleasesTheTemporaryScreenAndCanRetry() {
        val snapshot = NavigationSnapshot(
            Tab.Home,
            mapOf(Tab.Home to listOf(Route.Home, post).map { it.toPersistedRoute() }),
        )
        val f = fixture(snapshot)
        onMain {
            assertEquals(listOf(post), f.screens.map { it.route })
            f.startSwipe()
        }
        transition(f) { f.touch(MotionEvent.ACTION_CANCEL, 500f, 300) }
        onMain {
            assertTrue(f.events.contains(Route.Home to "destroyed"))
            assertNull(f.screen(Route.Home).parent)
            f.assertTop(post)
            f.events.clear()
            f.startSwipe()
            assertEquals(2, f.screens.count { it.route == Route.Home })
        }
        transition(f) { f.touch(MotionEvent.ACTION_UP, 500f, 300) }
        onMain { f.assertTop(Route.Home) }
    }

    private fun fixture(snapshot: NavigationSnapshot? = null): Fixture {
        lateinit var f: Fixture
        onMain {
            f = Fixture(instrumentation.targetContext, snapshot)
            f.events.clear()
        }
        return f
    }

    private fun transition(f: Fixture, action: () -> Unit) {
        val done = CountDownLatch(1)
        onMain {
            f.onVisible = { done.countDown() }
            action()
        }
        assertTrue("Navigation did not finish", done.await(5, TimeUnit.SECONDS))
        onMain {
            f.onVisible = {}
            assertEquals(NavigationState.Idle, f.host.currentNavigationState)
        }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
}
