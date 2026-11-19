package com.axoo.features.posts.presentation.postdetail.components

import android.graphics.Rect
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.engine.emoji.catalog.EmojiData
import com.axoo.engine.emoji.text.AnimatedEmojiSpan
import com.axoo.engine.emoji.text.AnimatedEmojiTextView
import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.features.posts.domain.Comment
import com.axoo.ui.theme.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentReplyPreviewLayoutTest {
    @Test
    fun replyPillReachesSendAndRetainsEmojiSpansAcrossWhitespaceChanges() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        EmojiData.configure(context)
        val emoji = EmojiData.emojis.first()
        val prefix = " \n👩🏽‍💻\t  "
        val comment = comment(prefix + emoji.fallback + "\n fine ", listOf(
            MessageEntity(offset = prefix.length, length = emoji.fallback.length, customEmojiId = emoji.id)))
        val composer = CommentComposerInputBar(context, {}, {}, { _, _, _, _ -> }, { _, _ -> })
        try {
            composer.bindReply("student", comment, enabled = true)
            for (width in listOf(320, 390, 744)) {
                composer.measure(View.MeasureSpec.makeMeasureSpec(context.dp(width), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                composer.layout(0, 0, composer.measuredWidth, composer.measuredHeight)
                val views = descendants(composer)
                val pill = views.filterIsInstance<CommentReplyPreviewView>().single()
                val send = CommentComposerInputBar::class.java.getDeclaredField("sendButton").apply { isAccessible = true }.get(composer) as View
                val pillBounds = Rect(0, 0, pill.width, pill.height)
                val sendBounds = Rect(0, 0, send.width, send.height)
                composer.offsetDescendantRectToMyCoords(pill, pillBounds)
                composer.offsetDescendantRectToMyCoords(send, sendBounds)
                assertEquals(context.dp(12), composer.height - sendBounds.bottom)
                assertEquals(sendBounds.right, pillBounds.right)
                assertEquals(context.dp(8), pillBounds.left)
                val preview = views.filterIsInstance<AnimatedEmojiTextView>().single()
                val text = preview.text as Spanned
                val span = text.getSpans(0, text.length, AnimatedEmojiSpan::class.java).single()
                assertEquals("👩🏽‍💻 \uFFFC fine", text.toString())
                assertEquals("👩🏽‍💻 ".length, text.getSpanStart(span))
                assertEquals(emoji.id, span.emojiIndex)
                assertEquals(context.dp(16), span.renderSizePx)
                assertTrue(span.renderSizePx <= context.dp(32))
            }
            composer.bindReply(null, null, enabled = true)
            assertTrue(descendants(composer).filterIsInstance<AnimatedEmojiTextView>().single().text.isEmpty())
        } finally { composer.dispose() }
    }

    @Test
    fun staticAndUnknownEmojiRemainReadableWithoutAnimatedSpans() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        EmojiData.configure(context)
        val result = buildCommentReplyText(comment("😀 ❤️", listOf(
            MessageEntity(type = MessageEntity.TYPE_STATIC_EMOJI, offset = 0, length = 2, customEmojiId = 1218),
            MessageEntity(offset = 3, length = 2, customEmojiId = Int.MAX_VALUE))), context.dp(16)) as Spanned
        assertEquals("😀 ❤️", result.toString())
        assertTrue(result.getSpans(0, result.length, AnimatedEmojiSpan::class.java).isEmpty())
    }

    @Test
    fun emptyReplyLeavesNoHeaderGap() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pill = CommentReplyPreviewView(context)
        pill.bind(null, null, enabled = false)
        pill.measure(View.MeasureSpec.makeMeasureSpec(context.dp(300), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        assertEquals(0, pill.measuredHeight)
        assertFalse(pill.isClickable)
        pill.dispose()
    }

    private fun comment(body: String, entities: List<MessageEntity>) = Comment(
        id = "reply", postId = "post", userId = "me", body = body, entities = entities, createdAt = "2026-10-03T12:00:00Z")
    private fun descendants(view: ViewGroup): List<View> = (0 until view.childCount).flatMap { index ->
        val child = view.getChildAt(index)
        listOf(child) + if (child is ViewGroup) descendants(child) else emptyList()
    }
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
