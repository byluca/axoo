package com.axoo.engine.emoji.picker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.graphics.Rect
import com.axoo.engine.emoji.animation.drawable.AnimatedEmojiDrawable
import com.axoo.engine.emoji.animation.model.AnimatedEmojiKey
import com.axoo.engine.lottie.LottieHost
import android.graphics.Paint
import android.text.SpannableString
import android.text.Spanned
import com.axoo.engine.emoji.text.AnimatedEmojiRenderer
import com.axoo.engine.emoji.text.AnimatedEmojiSpan
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.widget.ImageView
import com.axoo.engine.emoji.catalog.EmojiData
import com.axoo.ui.theme.dp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmojiFallbackTest {
    @Test fun cellStaysEmptyUntilAFrameIsReadyAndClearsOnReuse() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var cell: EmojiCellView
        lateinit var drawable: AnimatedEmojiDrawable
        val host = object : LottieHost {
            override val isAttachedForLottie = true
            override fun invalidateLottie(bounds: Rect?) = Unit
        }
        onMain {
            val context = instrumentation.targetContext
            EmojiData.configure(context)
            cell = EmojiCellView(context, context.dp(48))
            cell.layout(0, 0, context.dp(60), context.dp(60))
            drawable = AnimatedEmojiDrawable(context,
                AnimatedEmojiKey("picker-frame", EmojiData.categories.first().icon.assetPath, 96))
            cell.setAnimatedEmojiDrawable(drawable)
            assertFalse(renderHasPixels(cell))
            drawable.attachTo(host)
            drawable.requestPlayback(host, true)
        }
        try {
            val deadline = SystemClock.uptimeMillis() + 5_000
            var ready = false
            while (!ready && SystemClock.uptimeMillis() < deadline) {
                onMain { ready = drawable.hasDrawableContent }
                if (!ready) SystemClock.sleep(25)
            }
            assertTrue("The cell must eventually receive its animation", ready)
            onMain {
                assertTrue(renderHasPixels(cell))
                cell.clearContent()
                assertFalse(renderHasPixels(cell))
            }
        } finally { onMain { cell.clearContent(); drawable.dispose() } }
    }

    @Test fun categoryUsesABundledStaticImageImmediately() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        EmojiData.configure(context)
        val button = EmojiCategoryButton(context)
        button.bind(EmojiData.categories.first().id)
        button.measure(View.MeasureSpec.makeMeasureSpec(context.dp(44), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(context.dp(44), View.MeasureSpec.EXACTLY))
        button.layout(0, 0, button.measuredWidth, button.measuredHeight)
        assertTrue(button.getChildAt(0) is ImageView)
        assertTrue(renderHasPixels(button))
    }

    @Test fun textRendererStaysEmptyWhenTheAnimationHasNoFrame() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val host = View(context)
        val renderer = AnimatedEmojiRenderer(context, host)
        val span = AnimatedEmojiSpan(context.dp(48), emojiIndex = Int.MAX_VALUE,
            emojiName = "missing/emoji", fallbackChar = "🙂")
        val text = SpannableString("\uFFFC").apply { setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        val bitmap = Bitmap.createBitmap(context.dp(64), context.dp(64), Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            span.draw(canvas, text, 0, 1, context.dp(8).toFloat(), 0, context.dp(32), context.dp(64), Paint())
            renderer.updateFromText(text)
            renderer.draw(canvas)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertFalse("Reserve empty space before the TGS is ready", pixels.any { it ushr 24 != 0 })
        } finally { renderer.clear(); bitmap.recycle() }
    }

    private fun renderHasPixels(view: View): Boolean {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        return try {
            view.draw(Canvas(bitmap))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            pixels.any { it ushr 24 != 0 }
        } finally { bitmap.recycle() }
    }
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
