package com.axoo.features.posts.presentation.components

import android.text.Layout
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.presentation.postdetail.components.PostDetailCommentsHeaderView
import com.axoo.ui.theme.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativePostFullBodyTest {
    @Test
    fun feedAndDetailShowEveryLineWithoutEllipsisEvenAfterRebinding() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val body = (1..18).joinToString("\n") { "Riga $it: testo completo 👩🏽‍💻" }
        val post = Post(id = "long-post", userId = "author", universityId = "uniba",
            nickname = "Studente", body = body, createdAt = "2026-10-07T10:00:00Z")
        for (presentation in NativePostPresentation.entries) {
            val view = NativePostView(context, presentation)
            for (width in listOf(280, 390)) {
                fun measure() {
                    view.measure(View.MeasureSpec.makeMeasureSpec(context.dp(width), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    view.layout(0, 0, view.measuredWidth, view.measuredHeight)
                }
                view.bind(post.copy(body = "Breve"), {}, {}, {}, {}, { _, _ -> })
                measure()
                val shortHeight = view.height
                view.bind(post, {}, {}, {}, {}, { _, _ -> })
                measure()
                val layout = view.javaClass.getDeclaredField("bodyLayout").apply { isAccessible = true }.get(view) as Layout
                assertTrue(layout.lineCount >= 18)
                assertEquals(body.length, layout.getLineEnd(layout.lineCount - 1))
                for (line in 0 until layout.lineCount) assertEquals(0, layout.getEllipsisCount(line))
                assertTrue(view.height > shortHeight)
                assertTrue(view.height > layout.height)
            }
        }
    }

    @Test
    fun commentsHeadingHasNoSelectableSortControls() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val header = PostDetailCommentsHeaderView(context)
        header.measure(View.MeasureSpec.makeMeasureSpec(context.dp(390), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        assertFalse(header.isClickable)
        assertFalse(header.performClick())
        assertTrue(header.measuredHeight < context.dp(60))
    }

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
