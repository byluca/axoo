package com.axoo.features.posts.presentation.components

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.domain.PostPoll
import com.axoo.features.posts.domain.PostPollOption
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativePostPollTest {
    private val original = Post("poll-post", "author", universityId = "campus", nickname = "Autore",
        body = "Cosa preferisci?", createdAt = "2026-10-25T00:00:00Z",
        poll = PostPoll(options = listOf(PostPollOption("a", "Un'opzione lunga che può occupare più righe"), PostPollOption("b", "Seconda opzione"))))

    @Test fun optionsStayInsideTheCardAtLargeFontSizesAndNarrowWidths() = onMain {
        for (fontScale in listOf(1f, 2f)) {
            val context = context().createConfigurationContext(Configuration(context().resources.configuration).apply { this.fontScale = fontScale })
            for (widthDp in listOf(280, 390)) {
                val view = NativePostView(context)
                bind(view, original)
                measure(view, widthDp)
                val renderer = field<PostPollRenderer>(view, "pollRenderer")
                val card = field<RectF>(view, "cardRect")
                assertTrue(card.contains(renderer.bounds))
                val first = renderer.optionBounds(0)!!
                val second = renderer.optionBounds(1)!!
                assertTrue(first.bottom <= second.top)
                val height = view.measuredHeight
                bind(view, original.copy(poll = original.poll!!.withOptimisticVote("a").copy(isSending = false)))
                measure(view, widthDp)
                assertEquals("Voting must not shift the card", height, view.measuredHeight)
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                bitmap.recycle()
                view.recycle()
            }
        }
    }

    @Test fun selectedAndPendingPollsIgnoreFurtherTapsAndCancelledTouchesDoNotVote() = onMain {
        val view = NativePostView(context())
        val choices = mutableListOf<String>()
        fun render(post: Post) { bind(view, post) { choices.add(it) }; measure(view, 390) }
        render(original)
        val renderer = field<PostPollRenderer>(view, "pollRenderer")
        val row = renderer.optionBounds(0)!!
        touch(view, MotionEvent.ACTION_DOWN, row)
        touch(view, MotionEvent.ACTION_CANCEL, row)
        touch(view, MotionEvent.ACTION_UP, row)
        assertTrue(choices.isEmpty())
        tap(view, row)
        assertEquals(listOf("a"), choices)
        val pending = original.poll!!.withOptimisticVote("a")
        render(original.copy(poll = pending))
        tap(view, renderer.optionBounds(1)!!)
        render(original.copy(poll = pending.copy(isSending = false)))
        tap(view, renderer.optionBounds(1)!!)
        assertEquals(listOf("a"), choices)
        view.recycle()
    }

    private fun bind(view: NativePostView, post: Post, vote: (String) -> Unit = {}) {
        view.bind(post, {}, {}, {}, {}, { _, _ -> }, onPollVote = { _, option -> vote(option) })
    }
    private fun tap(view: View, rect: RectF) { touch(view, MotionEvent.ACTION_DOWN, rect); touch(view, MotionEvent.ACTION_UP, rect) }
    private fun touch(view: View, action: Int, rect: RectF) {
        val now = SystemClock.uptimeMillis()
        MotionEvent.obtain(now, now, action, rect.centerX(), rect.centerY(), 0).also { view.onTouchEvent(it); it.recycle() }
    }
    private fun measure(view: View, widthDp: Int) {
        val width = (widthDp * view.resources.displayMetrics.density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, width, view.measuredHeight)
    }
    @Suppress("UNCHECKED_CAST") private fun <T> field(target: Any, name: String): T = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target) as T
    private fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
