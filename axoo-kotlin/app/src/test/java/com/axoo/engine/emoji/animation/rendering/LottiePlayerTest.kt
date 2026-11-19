package com.axoo.engine.emoji.animation.rendering

import android.graphics.Bitmap
import com.axoo.engine.lottie.RLottieAnimation
import com.axoo.engine.lottie.RLottieRenderPool
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Reproduces a render completing before launch returns its Job. */
class LottiePlayerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val frames = mutableListOf<Int>()
    private lateinit var player: LottiePlayer

    @Before
    fun setUp() {
        mockkObject(EmojiBitmapPool, AnimationFrameRenderer)
        mockkStatic("com.axoo.engine.lottie.LottieRenderPoolKt")
        every { RLottieRenderPool } returns object : ExecutorCoroutineDispatcher() {
            override val executor = Executor { it.run() }
            override fun isDispatchNeeded(context: CoroutineContext) = false
            override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
            override fun close() = Unit
        }
        every { EmojiBitmapPool.acquire(any(), any()) } answers { mockk<Bitmap>(relaxed = true) }
        every { EmojiBitmapPool.release(any()) } just Runs
        every { AnimationFrameRenderer.render(any(), any(), any()) } returns
            AnimationFrameRenderer.Result.Success
        player = LottiePlayer(
            animation = RLottieAnimation(
                totalFrames = 137, frameRate = 30f, width = 92, height = 92,
                nativePointer = 1L, destroyNative = {},
            ),
            widthPx = 92, heightPx = 92, scope = scope,
            onFrameReady = frames::add,
        )
    }

    @After
    fun tearDown() {
        if (::player.isInitialized) runBlocking { player.close() }
        scope.cancel()
        unmockkStatic("com.axoo.engine.lottie.LottieRenderPoolKt")
        unmockkObject(EmojiBitmapPool, AnimationFrameRenderer)
    }

    @Test
    fun `suspending a queued render skips native work and allows resume`() {
        val pending = java.util.ArrayDeque<Runnable>()
        every { RLottieRenderPool } returns object : ExecutorCoroutineDispatcher() {
            override val executor = Executor { pending.addLast(it) }
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                pending.addLast(block)
            }
            override fun close() = Unit
        }
        player.renderInitialFrame()
        player.suspend()
        while (pending.isNotEmpty()) pending.removeFirst().run()
        io.mockk.verify(exactly = 0) { AnimationFrameRenderer.render(any(), any(), any()) }
        assertEquals(emptyList<Int>(), frames)

        player.renderInitialFrame()
        while (pending.isNotEmpty()) pending.removeFirst().run()
        assertEquals(listOf(0), frames)
    }

    @Test
    fun `resume during cancellation keeps the latest frame request`() {
        val pending = java.util.ArrayDeque<Runnable>()
        every { RLottieRenderPool } returns object : ExecutorCoroutineDispatcher() {
            override val executor = Executor { pending.addLast(it) }
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                pending.addLast(block)
            }
            override fun close() = Unit
        }
        player.play()
        player.onVsync(1_000_000_000L)
        player.pause()
        player.play()
        player.onVsync(2_000_000_000L)
        player.onVsync(2_100_000_000L)
        while (pending.isNotEmpty()) pending.removeFirst().run()
        assertEquals(listOf(3), frames)
    }

    @Test
    fun `small emoji publishes only forty five frames per second without slowing playback`() {
        runBlocking { player.close() }
        player = LottiePlayer(
            animation = RLottieAnimation(
                totalFrames = 120, frameRate = 60f, width = 64, height = 64,
                nativePointer = 1L, destroyNative = {},
            ),
            widthPx = 64, heightPx = 64, scope = scope, maximumFrameRate = 45f,
            onFrameReady = frames::add,
        )
        player.renderInitialFrame()
        player.play()
        repeat(60) { player.onVsync(1_000_000_000L + it * 16_666_667L) }
        assertEquals(45, frames.size)
        assertEquals(58, frames.last())
    }

    @Test
    fun `instant render completion does not block following frames`() {
        player.renderInitialFrame()
        player.play()
        repeat(100) { player.onVsync(1_000_000_000L + it * 100_000_000L) }

        assertEquals(100, frames.size)
        assertEquals(23, frames.last()) // (99 * 3) modulo 137
    }

    @Test
    fun `pause and resume continue after an instant render`() {
        player.renderInitialFrame()
        player.play()
        player.onVsync(1_000_000_000L)
        player.onVsync(1_100_000_000L)
        player.pause()
        player.onVsync(2_000_000_000L)
        player.play()
        player.onVsync(3_000_000_000L)
        player.onVsync(3_100_000_000L)

        assertEquals(listOf(0, 3, 6), frames)
    }
}
