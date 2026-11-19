package com.axoo.ui.text

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ReplacementSpan
import android.text.style.URLSpan
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebLinksTest {
    @Test fun wrappedLinkAfterEmojiOpensBrowserAndPlainTextDoesNot() = onMain {
        val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val original = SpannableStringBuilder("\uFFFC testo https://example.com/long/path?x=1&y=2, fine")
        val emoji = object : ReplacementSpan() {
            override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?) = 24
            override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {}
        }
        original.setSpan(emoji, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val text = withWebLinks(original) as Spanned
        assertEquals(original.toString(), text.toString())
        assertSame(emoji, text.getSpans(0, 1, ReplacementSpan::class.java).single())
        val link = text.getSpans(0, text.length, URLSpan::class.java).single()
        assertEquals("https://example.com/long/path?x=1&y=2", link.url)
        assertEquals(',', text[text.getSpanEnd(link)])
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, TextPaint().apply { textSize = 18f }, 140).build()
        val touch = WebLinkTouch(View(context))
        fun tap(offset: Int): Boolean {
            val line = layout.getLineForOffset(offset)
            val x = (layout.getPrimaryHorizontal(offset) + layout.getPrimaryHorizontal(offset + 1)) / 2
            val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
            val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, x, y, 0)
            touch.onTouch(down, layout, 0f, 0f)
            val consumed = touch.onTouch(up, layout, 0f, 0f)
            down.recycle(); up.recycle()
            return consumed
        }
        assertFalse(tap(text.toString().indexOf("testo") + 1))
        assertTrue(context.opened.isEmpty())
        val end = text.getSpanEnd(link) - 3
        assertTrue(layout.getLineForOffset(end) > layout.getLineForOffset(text.getSpanStart(link)))
        assertTrue(tap(end))
        assertEquals(Intent.ACTION_VIEW, context.opened.single().action)
        assertEquals(link.url, context.opened.single().data.toString())
    }

    @Test fun canceledGestureDoesNotOpenLink() = onMain {
        val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val text = withWebLinks("https://example.com")
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, TextPaint().apply { textSize = 18f }, 300).build()
        val touch = WebLinkTouch(View(context))
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(0, 1, action, 10f, 10f, 0)
            assertFalse(touch.onTouch(event, layout, 0f, 0f))
            event.recycle()
        }
        assertTrue(context.opened.isEmpty())
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val opened = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) { opened.add(intent) }
    }
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
