package com.axoo.features.posts.presentation.stickers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.axoo.application.bootstrap.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StickerCropTest {
    @Test fun squareCropPreservesTransparencyAndPanningCannotExposeOutsideThePhoto() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val source = Bitmap.createBitmap(900, 300, Bitmap.Config.ARGB_8888)
                Canvas(source).apply {
                    drawRect(0f, 0f, 300f, 300f, Paint().apply { color = Color.RED })
                    drawRect(600f, 0f, 900f, 300f, Paint().apply { color = Color.BLUE })
                }
                val crop = StickerCropView(activity).apply { image = source }
                crop.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY))
                crop.layout(0, 0, 300, 500)
                crop.cropped().also {
                    assertEquals(512, it.width); assertEquals(512, it.height)
                    assertEquals(0, Color.alpha(it.getPixel(256, 256)))
                    it.recycle()
                }
                drag(crop, 150f, 3000f)
                crop.cropped().also {
                    assertEquals(Color.RED, it.getPixel(256, 256))
                    assertEquals(Color.RED, it.getPixel(0, 0))
                    assertEquals(Color.RED, it.getPixel(511, 511))
                    it.recycle()
                }
                drag(crop, 150f, -3000f)
                crop.cropped().also {
                    assertEquals(Color.BLUE, it.getPixel(256, 256))
                    assertEquals(Color.BLUE, it.getPixel(511, 511))
                    it.recycle()
                }
                source.recycle()
            }
        }
    }

    private fun drag(view: View, from: Float, to: Float) {
        val now = SystemClock.uptimeMillis()
        for ((action, x) in listOf(MotionEvent.ACTION_DOWN to from, MotionEvent.ACTION_MOVE to to, MotionEvent.ACTION_UP to to)) {
            MotionEvent.obtain(now, now + 20, action, x, 200f, 0).also { view.dispatchTouchEvent(it); it.recycle() }
        }
    }
}
