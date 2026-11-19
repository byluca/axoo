package com.axoo.ui.sheets

import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.application.bootstrap.MainActivity
import com.axoo.features.posts.presentation.postdetail.components.CommentOptionsSheet
import com.axoo.features.posts.presentation.sheets.PostOptionsSheet
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class OptionsSheetTest {
    @Test
    fun ownPostActionsOpenFromTheScreenHostAndEditRunsAfterDismissal() {
        assertActionsVisibleAndSelectable(
            labels = listOf("Modifica post", "Elimina post"),
            selectedLabel = "Modifica post",
        ) { host, onSelected, onDismissed ->
            val handle = PostOptionsSheet.present(
                anchor = host,
                isOwnPost = true,
                onReport = {},
                onEdit = onSelected,
                onDelete = {},
                onDismissed = onDismissed,
            )
            handle::dismiss
        }
    }

    @Test
    fun otherPostActionsOpenFromTheScreenHostAndReportRunsAfterDismissal() {
        assertActionsVisibleAndSelectable(
            labels = listOf("Nascondi per me", "Segnala post", "Blocca autore"),
            selectedLabel = "Segnala post",
        ) { host, onSelected, onDismissed ->
            val handle = PostOptionsSheet.present(
                anchor = host,
                isOwnPost = false,
                onReport = onSelected,
                onEdit = {},
                onDelete = {},
                onDismissed = onDismissed,
            )
            handle::dismiss
        }
    }

    @Test
    fun commentActionsOpenFromTheScreenHostAndDeleteRunsAfterDismissal() {
        assertActionsVisibleAndSelectable(
            labels = listOf("Modifica commento", "Elimina commento"),
            selectedLabel = "Elimina commento",
        ) { host, onSelected, onDismissed ->
            val handle = CommentOptionsSheet.present(
                anchor = host,
                isOwnComment = true,
                onReport = {},
                onEdit = {},
                onDelete = onSelected,
                onDismissed = onDismissed,
            )
            handle::dismiss
        }
    }

    private fun assertActionsVisibleAndSelectable(
        labels: List<String>,
        selectedLabel: String,
        present: (View, () -> Unit, () -> Unit) -> (() -> Unit),
    ) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var host: FrameLayout
            var dismiss: (() -> Unit)? = null
            val laidOut = CountDownLatch(1)
            val selected = CountDownLatch(1)
            val events = Collections.synchronizedList(mutableListOf<String>())
            scenario.onActivity { activity ->
                // The real feed/detail passes its full-screen host, not a child button.
                host = FrameLayout(activity)
                activity.setContentView(host, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ))
                host.doOnLayout { laidOut.countDown() }
            }
            assertTrue("Screen host did not finish layout", laidOut.await(3, TimeUnit.SECONDS))
            try {
                scenario.onActivity {
                    dismiss = present(host, {
                        events.add("selected")
                        selected.countDown()
                    }, {
                        events.add("dismissed")
                    })
                }
                val visibleLabels = awaitVisibleLabels(labels)
                tap(visibleLabels.getValue(selectedLabel))
                assertTrue("Selection did not complete", selected.await(3, TimeUnit.SECONDS))
                InstrumentationRegistry.getInstrumentation().runOnMainSync { dismiss?.invoke() }
                assertEquals(listOf("dismissed", "selected"), events.toList())
            } finally {
                InstrumentationRegistry.getInstrumentation().runOnMainSync { dismiss?.invoke() }
            }
        }
    }

    private fun awaitVisibleLabels(labels: List<String>): Map<String, TextView> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + 3_000
        var visible = emptyMap<String, TextView>()
        do {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                visible = WindowInspector.getGlobalWindowViews().asSequence()
                    .flatMap(::descendants)
                    .filterIsInstance<TextView>()
                    .filter { view ->
                        val clipped = Rect()
                        view.isShown && view.getGlobalVisibleRect(clipped) &&
                            clipped.width() == view.width && clipped.height() == view.height
                    }
                    .associateBy { it.text.toString() }
            }
            if (labels.all(visible::containsKey)) return visible
            SystemClock.sleep(16)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Menu actions must be fully visible from the screen host. " +
            "Expected: $labels; visible: ${visible.keys}")
    }

    private fun tap(view: View) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val location = IntArray(2)
        var x = 0f
        var y = 0f
        instrumentation.runOnMainSync {
            view.getLocationOnScreen(location)
            x = location[0] + view.width / 2f
            y = location[1] + view.height / 2f
        }
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue("Menu tap was not injected", instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) repeat(view.childCount) { yieldAll(descendants(view.getChildAt(it))) }
    }
}
