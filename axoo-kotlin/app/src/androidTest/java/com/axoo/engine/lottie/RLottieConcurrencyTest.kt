package com.axoo.engine.lottie

import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RLottieConcurrencyTest {

    private val LOG_TAG = "RLottie-Diag"
    private val RENDER_ITERATIONS = 1000
    private val ASSET_A = "axooji/animations/focaccia-peroni/rocket.tgs"
    private val ASSET_B = "axooji/animations/via-re-david/loading.tgs"
    private val COMPLEX_ASSET_A = "axooji/animations/via-orabona/star-struck-cat.tgs"
    private val COMPLEX_ASSET_B = "axooji/animations/storie-vecchio-sud/vomit.tgs"

    private fun loadAssetString(path: String): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.assets.open(path).use { stream ->
            if (path.endsWith(".tgs")) {
                GZIPInputStream(stream).bufferedReader().readText()
            } else {
                stream.bufferedReader().readText()
            }
        }
    }

    private fun frameCount(ptr: Long): Int =
        RLottieLib.getInfo(ptr)?.getOrNull(0)?.toInt()?.coerceAtLeast(1) ?: 1

    @Test
    fun exclusive_loads_of_same_asset_create_distinct_native_instances() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = RLottieNativeLoader.load(
            assetPath = ASSET_A,
            context = context,
        )
        val second = RLottieNativeLoader.load(
            assetPath = ASSET_A,
            context = context,
        )

        try {
            assertNotNull(first)
            assertNotNull(second)
            assertNotEquals(
                first!!.withNativePointer { it },
                second!!.withNativePointer { it },
            )
        } finally {
            first?.close()
            second?.close()
        }
    }

    @Test
    fun scenario0_DifferentPointers_SingleRenderThread() = runBlocking {
        configureRLottieRenderPool(1)
        Log.i(LOG_TAG, "\n--- SCENARIO 0: Different Pointers, Single Render Thread ---")
        val json1 = loadAssetString(ASSET_A)
        val json2 = loadAssetString(ASSET_B)

        val ptr1 = RLottieLib.createFromData(json1, "s0_alien")
        val ptr2 = RLottieLib.createFromData(json2, "s0_ant")
        val frames1 = frameCount(ptr1)
        val frames2 = frameCount(ptr2)

        val bmp1 = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val bmp2 = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)

        val errors1 = AtomicInteger(0)
        val errors2 = AtomicInteger(0)

        val j1 = launch(RLottieRenderPool) {
            for (i in 0 until RENDER_ITERATIONS) {
                val res = RLottieLib.renderFrame(ptr1, i % frames1, bmp1)
                if (res != RLottieLib.RENDER_SUCCESS) errors1.incrementAndGet()
            }
        }
        val j2 = launch(RLottieRenderPool) {
            for (i in 0 until RENDER_ITERATIONS) {
                val res = RLottieLib.renderFrame(ptr2, i % frames2, bmp2)
                if (res != RLottieLib.RENDER_SUCCESS) errors2.incrementAndGet()
            }
        }

        j1.join()
        j2.join()

        Log.i(LOG_TAG, "Scenario 0 Complete. Errors: ptr1=${errors1.get()}, ptr2=${errors2.get()}")
        assertEquals(0, errors1.get())
        assertEquals(0, errors2.get())

        RLottieLib.destroy(ptr1)
        RLottieLib.destroy(ptr2)
        bmp1.recycle()
        bmp2.recycle()
    }

    @Test
    fun scenario1_DifferentPointers_DifferentAssets() = runBlocking {
        configureRLottieRenderPool(2)
        Log.i(LOG_TAG, "\n--- SCENARIO 1: Different Pointers, Different Assets ---")
        val json1 = loadAssetString(ASSET_A)
        val json2 = loadAssetString(ASSET_B)

        val ptr1 = RLottieLib.createFromData(json1, "s1_alien")
        val ptr2 = RLottieLib.createFromData(json2, "s1_ant")
        val frames1 = frameCount(ptr1)
        val frames2 = frameCount(ptr2)

        val bmp1 = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val bmp2 = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)

        val errors1 = AtomicInteger(0)
        val errors2 = AtomicInteger(0)

        val j1 = launch(RLottieRenderPool) {
            for (i in 0 until RENDER_ITERATIONS) {
                val res = RLottieLib.renderFrame(ptr1, i % frames1, bmp1)
                if (res != RLottieLib.RENDER_SUCCESS) errors1.incrementAndGet()
            }
        }
        val j2 = launch(RLottieRenderPool) {
            for (i in 0 until RENDER_ITERATIONS) {
                val res = RLottieLib.renderFrame(ptr2, i % frames2, bmp2)
                if (res != RLottieLib.RENDER_SUCCESS) errors2.incrementAndGet()
            }
        }

        j1.join()
        j2.join()

        Log.i(LOG_TAG, "Scenario 1 Complete. Errors: ptr1=${errors1.get()}, ptr2=${errors2.get()}")
        assertEquals(0, errors1.get())
        assertEquals(0, errors2.get())
        
        RLottieLib.destroy(ptr1)
        RLottieLib.destroy(ptr2)
        bmp1.recycle()
        bmp2.recycle()
    }

    @Test
    fun scenario2_SamePointer_ParallelRender() = runBlocking {
        configureRLottieRenderPool(2)
        Log.i(LOG_TAG, "\n--- SCENARIO 2: Same Pointer, Parallel Render ---")
        val json = loadAssetString(ASSET_A)
        val ptr = RLottieLib.createFromData(json, "s2_alien")
        val frames = frameCount(ptr)

        val bmp1 = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val bmp2 = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)

        val busyCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        val j1 = launch(RLottieRenderPool) {
            for (i in 0 until 500) {
                val res = RLottieLib.renderFrame(ptr, i % frames, bmp1)
                if (res == RLottieLib.RENDER_BUSY) busyCount.incrementAndGet()
                else if (res != RLottieLib.RENDER_SUCCESS) errorCount.incrementAndGet()
            }
        }
        val j2 = launch(RLottieRenderPool) {
            for (i in 0 until 500) {
                val res = RLottieLib.renderFrame(ptr, i % frames, bmp2)
                if (res == RLottieLib.RENDER_BUSY) busyCount.incrementAndGet()
                else if (res != RLottieLib.RENDER_SUCCESS) errorCount.incrementAndGet()
            }
        }

        j1.join()
        j2.join()

        Log.i(LOG_TAG, "Scenario 2 Complete. Busy: ${busyCount.get()}, Errors: ${errorCount.get()}")
        assertEquals(0, errorCount.get())
        assertTrue("same pointer should reject concurrent renders as BUSY", busyCount.get() > 0)
        
        RLottieLib.destroy(ptr)
        bmp1.recycle()
        bmp2.recycle()
    }

    @Test
    fun scenario3_SameAsset_IndependentPointers() = runBlocking {
        configureRLottieRenderPool(2)
        Log.i(LOG_TAG, "\n--- SCENARIO 3: Same Asset, Independent Pointers ---")
        // Load the exact same JSON into two different instances to verify if underlying 
        // string data or resource sharing causes issues.
        val json = loadAssetString(ASSET_A)
        val ptr1 = RLottieLib.createFromData(json, "s3_alien_A")
        val ptr2 = RLottieLib.createFromData(json, "s3_alien_B")
        val frames1 = frameCount(ptr1)
        val frames2 = frameCount(ptr2)

        val bmp1 = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val bmp2 = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)

        val errors1 = AtomicInteger(0)
        val errors2 = AtomicInteger(0)

        val j1 = launch(RLottieRenderPool) {
            for (i in 0 until RENDER_ITERATIONS) {
                val res = RLottieLib.renderFrame(ptr1, i % frames1, bmp1)
                if (res != RLottieLib.RENDER_SUCCESS) errors1.incrementAndGet()
            }
        }
        val j2 = launch(RLottieRenderPool) {
            for (i in 0 until RENDER_ITERATIONS) {
                val res = RLottieLib.renderFrame(ptr2, i % frames2, bmp2)
                if (res != RLottieLib.RENDER_SUCCESS) errors2.incrementAndGet()
            }
        }

        j1.join()
        j2.join()

        Log.i(LOG_TAG, "Scenario 3 Complete. Errors: ptr1=${errors1.get()}, ptr2=${errors2.get()}")
        assertEquals(0, errors1.get())
        assertEquals(0, errors2.get())

        RLottieLib.destroy(ptr1)
        RLottieLib.destroy(ptr2)
        bmp1.recycle()
        bmp2.recycle()
    }

    @Test
    fun scenario4_ComplexAssets() = runBlocking {
        configureRLottieRenderPool(2)
        Log.i(LOG_TAG, "\n--- SCENARIO 4: Complex Assets ---")
        // Using assets that heavily utilize strokes, gradients, and masks
        val json1 = loadAssetString(COMPLEX_ASSET_A)
        val json2 = loadAssetString(COMPLEX_ASSET_B)
        
        val ptr1 = RLottieLib.createFromData(json1, "s4_complex_A")
        val ptr2 = RLottieLib.createFromData(json2, "s4_complex_B")
        val frames1 = frameCount(ptr1)
        val frames2 = frameCount(ptr2)

        val bmp1 = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val bmp2 = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)

        val errors1 = AtomicInteger(0)
        val errors2 = AtomicInteger(0)

        val j1 = launch(RLottieRenderPool) {
            for (i in 0 until RENDER_ITERATIONS) {
                val res = RLottieLib.renderFrame(ptr1, i % frames1, bmp1)
                if (res != RLottieLib.RENDER_SUCCESS) errors1.incrementAndGet()
            }
        }
        val j2 = launch(RLottieRenderPool) {
            for (i in 0 until RENDER_ITERATIONS) {
                val res = RLottieLib.renderFrame(ptr2, i % frames2, bmp2)
                if (res != RLottieLib.RENDER_SUCCESS) errors2.incrementAndGet()
            }
        }

        j1.join()
        j2.join()

        Log.i(LOG_TAG, "Scenario 4 Complete. Errors: ptr1=${errors1.get()}, ptr2=${errors2.get()}")
        assertEquals(0, errors1.get())
        assertEquals(0, errors2.get())

        RLottieLib.destroy(ptr1)
        RLottieLib.destroy(ptr2)
        bmp1.recycle()
        bmp2.recycle()
    }
}
