package com.axoo.engine.emoji.animation.rendering

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.engine.emoji.animation.drawable.*
import com.axoo.engine.emoji.animation.model.*
import com.axoo.engine.emoji.animation.time.*
import com.axoo.engine.lottie.RLottieNativeLoader
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimationFrameRendererTest {

    @Test
    fun real_tgs_frame_is_rendered_into_bitmap() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val animation = RLottieNativeLoader.load(assetPath = ASSET, context = context)
        requireNotNull(animation)

        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        try {
            val result = withContext(Dispatchers.Default) {
                AnimationFrameRenderer.render(
                    animation = animation,
                    frameIndex = animation.totalFrames / 2,
                    target = bitmap,
                )
            }

            assertEquals(AnimationFrameRenderer.Result.Success, result)
            assertTrue(bitmap.containsVisiblePixels())
        } finally {
            bitmap.recycle()
            animation.close()
        }
    }

    @Test
    fun render_after_close_is_rejected_without_using_native_pointer() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val animation = RLottieNativeLoader.load(assetPath = ASSET, context = context)
        requireNotNull(animation)
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)

        try {
            animation.close()

            val result = AnimationFrameRenderer.render(animation, 0, bitmap)

            assertEquals(AnimationFrameRenderer.Result.AnimationClosed, result)
        } finally {
            bitmap.recycle()
            animation.close()
        }
    }

    @Test
    fun yellow_face_keeps_rgb_channels_in_native_rendering() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val animation = RLottieNativeLoader.load(assetPath = YELLOW_ASSET, context = context)
        requireNotNull(animation)
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)

        try {
            var maxYellow = 0
            var maxCyan = 0
            val framesToCheck = min(animation.totalFrames, 24)
            for (frame in 0 until framesToCheck) {
                val result = withContext(Dispatchers.Default) {
                    AnimationFrameRenderer.render(animation, frame, bitmap)
                }
                assertEquals(AnimationFrameRenderer.Result.Success, result)
                val colors = bitmap.yellowAndCyanPixels()
                maxYellow = maxOf(maxYellow, colors.yellow)
                maxCyan = maxOf(maxCyan, colors.cyan)
            }

            assertTrue(
                "Native render lost yellow: yellow=$maxYellow cyan=$maxCyan",
                maxYellow > 100,
            )
            assertTrue(
                "Native render has swapped channels: yellow=$maxYellow cyan=$maxCyan",
                maxYellow > maxCyan * 10,
            )
        } finally {
            bitmap.recycle()
            animation.close()
        }
    }

    private fun Bitmap.containsVisiblePixels(): Boolean {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.any { pixel -> pixel ushr 24 != 0 }
    }

    private fun Bitmap.yellowAndCyanPixels(): ColorCounts {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        var yellow = 0
        var cyan = 0
        pixels.forEach { pixel ->
            if (Color.alpha(pixel) < 200) return@forEach
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            if (red > 200 && green > 150 && blue < 80) yellow += 1
            if (blue > 200 && green > 150 && red < 80) cyan += 1
        }
        return ColorCounts(yellow, cyan)
    }

    private companion object {
        const val ASSET = "axooji/animations/focaccia-peroni/rocket.tgs"
        const val YELLOW_ASSET = "axooji/animations/via-re-david/loading.tgs"
    }

    private data class ColorCounts(val yellow: Int, val cyan: Int)
}
