package com.axoo.features.posts.presentation.components

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.features.posts.domain.Comment
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.presentation.postdetail.CommentUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeCardFontScaleTest {
    @Test
    fun canvasUsesTheSameTextScaleAsNativeTextViewsAtEveryWidth() = onMain {
        for (scale in listOf(1f, 1.3f, 2f)) {
            val context = scaledContext(scale)
            val post = postView(context)
            val comment = commentView(context)
            for (widthDp in listOf(280, 390)) {
                measure(post, widthDp)
                measure(comment, widthDp)
                assertNativeSize(post, "bodyPaint", 17f)
                assertNativeSize(post, "authorPaint", 15f)
                assertNativeSize(comment, "bodyPaint", 16f)
                assertNativeSize(comment, "authorPaint", 15f)
                for (view in listOf(post, comment)) {
                    assertNativeSize(view, "metaPaint", 13f)
                    assertNativeSize(view, "scorePaint", 13f)
                    assertNativeSize(view, "actionPaint", 13f)
                }
            }
        }
    }

    @Test
    fun largerTextMakesThePostAndCommentTaller() = onMain {
        val normal = scaledContext(1f)
        val large = scaledContext(2f)
        val normalPost = postView(normal)
        val largePost = postView(large)
        val normalComment = commentView(normal)
        val largeComment = commentView(large)
        for (view in listOf(normalPost, largePost, normalComment, largeComment)) measure(view, 390)
        assertTrue(largePost.measuredHeight > normalPost.measuredHeight)
        assertTrue(largeComment.measuredHeight > normalComment.measuredHeight)
        for (view in listOf(largePost, largeComment)) {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap.recycle()
        }
    }

    @Test
    fun replyAndVotesUseSeparateRowsWhenLargeTextDoesNotFit() = onMain {
        val view = commentView(scaledContext(2f))
        view.bind(CommentUiModel(comment.copy(upvotes = 999999)))
        measure(view, 240)
        val reply = field<RectF>(view, "replyRect")
        val upvote = field<RectF>(view, "upvoteRect")
        assertTrue("Rispondi $reply, voti $upvote", reply.bottom <= upvote.top)
        val replyPaint = field<TextPaint>(view, "actionPaint")
        assertTrue(reply.height() >= replyPaint.descent() - replyPaint.ascent())
        val scorePaint = field<TextPaint>(view, "scorePaint")
        assertTrue(upvote.height() >= scorePaint.descent() - scorePaint.ascent())
        assertTrue(upvote.bottom < view.measuredHeight)
    }

    @Test
    fun changingToALongerScoreReservesMoreSpaceBetweenVoteButtons() = onMain {
        val context = scaledContext(2f)
        val view = commentView(context)
        measure(view, 390)
        val oldSpace = field<RectF>(view, "downvoteRect").left - field<RectF>(view, "upvoteRect").right
        view.bindVote(CommentUiModel(comment.copy(upvotes = 999999)))
        assertTrue(view.isLayoutRequested)
        measure(view, 390)
        val newSpace = field<RectF>(view, "downvoteRect").left - field<RectF>(view, "upvoteRect").right
        assertTrue(newSpace > oldSpace)
        assertTrue(newSpace >= field<TextPaint>(view, "scorePaint").measureText("999999"))
    }

    @Test
    fun rollingScoreDoesNotClipLargeGlyphs() {
        val actual = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val expected = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 96f }
            drawRollingNumber(Canvas(actual), paint, 918, 918, 1f, 0f, 256f, 128f, 12f, 2f)
            Canvas(expected).drawText("918", (256f - paint.measureText("918")) / 2f,
                128f - (paint.ascent() + paint.descent()) / 2f, paint)
            assertTrue(actual.sameAs(expected))
        } finally {
            actual.recycle()
            expected.recycle()
        }
    }

    private fun assertNativeSize(view: View, paintName: String, sizeSp: Float) {
        val nativeSize = TextView(view.context).apply { textSize = sizeSp }.textSize
        assertEquals(nativeSize, field<TextPaint>(view, paintName).textSize, 0.01f)
    }

    private fun scaledContext(scale: Float): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply { fontScale = scale })
    }

    private fun postView(context: Context) = NativePostView(context, NativePostPresentation.Detail).apply {
        bind(post, {}, {}, {}, {}, { _, _ -> })
    }

    private fun commentView(context: Context) = NativeCommentView(context).apply {
        bind(CommentUiModel(comment))
    }

    private fun measure(view: View, widthDp: Int) {
        val width = (view.resources.displayMetrics.density * widthDp).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(view: View, name: String): T =
        view.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(view) as T

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private val body = "Un testo abbastanza lungo da andare a capo quando aumentano le dimensioni del carattere."
    private val post = Post(id = "font-post", userId = "font-user", universityId = "font-university",
        nickname = "Anonimo", body = body, createdAt = "2026-10-05T10:00:00Z")
    private val comment = Comment(id = "font-comment", postId = "font-post", userId = "font-user",
        nickname = "Anonimo", body = body, createdAt = "2026-10-05T10:00:00Z")
}
