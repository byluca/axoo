package com.axoo.features.posts.presentation.postdetail.components

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.features.posts.domain.Comment
import com.axoo.ui.theme.dp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentReplyPreviewViewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun previewKeepsItsWidthAndReleasesAllHeightWhenClosed() = onMain {
        val context = instrumentation.targetContext
        val view = CommentReplyPreviewView(context)
        view.bind("Espresso", comment("Prima riga\nSeconda riga"), true)
        measure(view)
        assertEquals(context.dp(280), view.measuredWidth)
        assertTrue("Reply must keep its minimum height", view.measuredHeight >= context.dp(52))
        val labels = descendants(view).filterIsInstance<TextView>().toList()
        assertEquals(listOf("Rispondi a Espresso", "Prima riga Seconda riga"), labels.map { it.text.toString() })
        val visibleBounds = Rect(0, 0, view.width, view.height)
        labels.forEach { label ->
            val bounds = Rect(0, 0, label.width, label.height)
            view.offsetDescendantRectToMyCoords(label, bounds)
            assertTrue("Reply text must fit at the current font scale", visibleBounds.contains(bounds))
        }

        view.bind(null, null, true)
        measure(view)
        assertEquals(0, view.measuredHeight)
        view.dispose()
    }

    @Test fun onlyCloseButtonCancelsReplyAndSwitchingRecipientKeepsHeight() = onMain {
        val view = CommentReplyPreviewView(instrumentation.targetContext)
        var cancellations = 0
        view.setOnCancel { cancellations++ }
        view.bind("Espresso", comment("Commento"), true)
        measure(view)
        val height = view.measuredHeight
        view.getChildAt(0).performClick()
        assertEquals(0, cancellations)

        view.bind("Altro nickname molto lungo", comment("Altra risposta"), true)
        measure(view)
        assertEquals(height, view.measuredHeight)
        val close = CommentReplyPreviewView::class.java.getDeclaredField("close").apply { isAccessible = true }.get(view) as View
        assertEquals(instrumentation.targetContext.dp(48), close.measuredWidth)
        close.performClick()
        assertEquals(1, cancellations)
        view.bind("Espresso", comment("Commento"), false)
        assertFalse(close.isEnabled)
        view.dispose()
    }

    private fun measure(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(view.context.dp(280), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(view.context.dp(400), View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun descendants(group: ViewGroup): Sequence<View> = sequence {
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            yield(child)
            if (child is ViewGroup) yieldAll(descendants(child))
        }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun comment(body: String) = Comment(id = "reply", postId = "post", userId = "me",
        body = body, createdAt = "2026-10-03T12:00:00Z")
}
