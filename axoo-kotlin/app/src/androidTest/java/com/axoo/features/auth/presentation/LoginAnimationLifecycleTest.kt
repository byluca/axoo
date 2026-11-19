package com.axoo.features.auth.presentation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.application.bootstrap.MainActivity
import com.axoo.application.navigation.factory.DefaultScreenFactory
import com.axoo.application.navigation.host.NavigationHost
import com.axoo.application.navigation.host.NavigationState
import com.axoo.application.navigation.route.Route
import com.axoo.application.navigation.route.Tab
import com.axoo.application.navigation.route.toPersistedRoute
import com.axoo.ui.components.ArrowBackButton
import com.axoo.engine.emoji.animation.drawable.AnimatedLottieView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginAnimationLifecycleTest {
    @Test fun startReplacesIntroAndShowsTheLockWithoutExtraPlaybackCallbacks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var navigation: NavigationHost
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                navigation = NavigationHost(activity, DefaultScreenFactory({}, {}), initialTab = Tab.Auth)
                activity.setContentView(navigation)
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                descendants(navigation).filterIsInstance<TextView>().single { it.text == "Inizia" }.performClick()
            }
            val deadline = SystemClock.uptimeMillis() + 5_000
            do {
                var finished = false
                instrumentation.runOnMainSync { finished = navigation.currentNavigationState == NavigationState.Idle }
                if (finished) break
                SystemClock.sleep(25)
            } while (SystemClock.uptimeMillis() < deadline)
            lateinit var illustration: AnimatedLottieView
            instrumentation.runOnMainSync {
                assertEquals(NavigationState.Idle, navigation.currentNavigationState)
                assertEquals(listOf(Route.Login.toPersistedRoute()), navigation.saveState().stacks.getValue(Tab.Auth))
                illustration = descendants(navigation).filterIsInstance<AnimatedLottieView>().single()
                assertFalse(descendants(navigation).filterIsInstance<ArrowBackButton>().single().isShown)
                assertTrue(navigation.onBackPressed())
                assertFalse(navigation.pop())
            }
            assertTrue("Login must show the lock after clicking Inizia", waitForVisibleFrame(illustration))
        }
    }

    private fun waitForVisibleFrame(view: AnimatedLottieView): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            instrumentation.runOnMainSync {
                if (view.isAttachedForLottie && view.width > 0 && view.height > 0) {
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    try {
                        view.draw(Canvas(bitmap))
                        val pixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        ready = pixels.any { it ushr 24 != 0 }
                    } finally { bitmap.recycle() }
                }
            }
            if (ready) return true
            SystemClock.sleep(25)
        }
        return false
    }

    private fun descendants(group: ViewGroup): List<View> = (0 until group.childCount).flatMap { index ->
        val child = group.getChildAt(index)
        listOf(child) + if (child is ViewGroup) descendants(child) else emptyList()
    }
}
