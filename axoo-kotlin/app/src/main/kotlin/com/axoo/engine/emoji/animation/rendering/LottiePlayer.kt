package com.axoo.engine.emoji.animation.rendering

import android.graphics.Bitmap
import com.axoo.engine.emoji.animation.time.AnimatedEmojiPlaybackClock
import com.axoo.engine.emoji.animation.time.PlaybackTimeline
import com.axoo.engine.lottie.RLottieAnimation
import com.axoo.engine.lottie.RLottieLib
import com.axoo.engine.lottie.RLottieRenderPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Calcola i frame in base al tempo e renderizza un solo frame alla volta.
 * Usa due bitmap: una visibile e una riservata al rendering in background.
 * Stato e callback sono gestiti sul main thread.
 */
internal class LottiePlayer(
    private val animation: RLottieAnimation,
    widthPx: Int,
    heightPx: Int,
    private val scope: CoroutineScope,
    private val playbackKey: String? = null,
    maximumFrameRate: Float = 60f,
    private val onFrameReady: (frameIndex: Int) -> Unit,
) {
    val totalFrames: Int = animation.totalFrames
    val frameRate: Float = animation.frameRate

    private val timeline = PlaybackTimeline(totalFrames, frameRate, maximumFrameRate)

    private var frontBitmap = EmojiBitmapPool.acquire(widthPx, heightPx)
    private var backBitmap = EmojiBitmapPool.acquire(widthPx, heightPx)

    private var renderJob: Job? = null
    private var playing = false
    private var disposed = false

    private var anchorFrame = 0
    private var anchorTimeNanos: Long? = null

    private var requestedFrame = 0
    private var publishedFrame = -1

    // Contatori letti dal monitor sul main thread; solo la fase nativa è cross-thread.
    internal var diagnosticPublishedFrames = 0L
        private set
    private var diagnosticVsyncs = 0L
    private var diagnosticLastVsyncNanos = 0L
    @Volatile private var diagnosticRenderStage = "idle"
    private var diagnosticLastFailure: String? = null

    internal fun diagnosticState(): String =
        "playing=$playing disposed=$disposed frame=$publishedFrame/$totalFrames " +
            "requested=$requestedFrame published=$diagnosticPublishedFrames ticks=$diagnosticVsyncs " +
            "tickAgeMs=${diagnosticLastVsyncNanos.takeIf { it > 0L }?.let { (System.nanoTime() - it) / 1_000_000L }} " +
            "render=$diagnosticRenderStage jobActive=${renderJob?.isActive} " +
            "jobCompleted=${renderJob?.isCompleted} lastFailure=$diagnosticLastFailure"

    val currentBitmap: Bitmap?
        get() = frontBitmap.takeIf { publishedFrame >= 0 && !it.isRecycled }

    fun renderInitialFrame() {
        requestFrame(initialFrame())
    }

    fun play() {
        if (disposed || playing) return
        playing = true
        if (playbackKey == null) {
            anchorFrame = publishedFrame.coerceAtLeast(0)
            anchorTimeNanos = null
        }
    }

    fun pause() {
        if (!playing) return
        playing = false
        val pausedFrame = publishedFrame.coerceAtLeast(0)
        anchorFrame = pausedFrame
        anchorTimeNanos = null
        requestedFrame = -1
        renderJob?.cancel()
    }

    fun suspend() {
        pause()
        requestedFrame = -1
        renderJob?.cancel()
    }

    fun onVsync(frameTimeNanos: Long) {
        if (EmojiPlaybackDiagnostics.enabled) {
            diagnosticVsyncs += 1
            diagnosticLastVsyncNanos = frameTimeNanos
        }
        if (disposed || !playing) return

        val sharedKey = playbackKey
        if (sharedKey != null) {
            requestFrame(
                AnimatedEmojiPlaybackClock.frameAt(
                    key = sharedKey,
                    timeline = timeline,
                    frameTimeNanos = frameTimeNanos,
                ),
            )
            return
        }
        val anchor = anchorTimeNanos ?: frameTimeNanos.also { anchorTimeNanos = it }
        requestFrame(
            timeline.frameAt(
                startFrame = anchorFrame,
                elapsedNanos = frameTimeNanos - anchor,
            ),
        )
    }

    private fun requestFrame(frameIndex: Int) {
        if (disposed || frameIndex !in 0 until totalFrames) return
        requestedFrame = frameIndex

        if (publishedFrame == frameIndex || renderJob != null) return
        startRender(frameIndex)
    }

    private fun initialFrame(): Int {
        val sharedKey = playbackKey ?: return 0
        return AnimatedEmojiPlaybackClock.frameAt(
            key = sharedKey,
            timeline = timeline,
            frameTimeNanos = System.nanoTime(),
        )
    }

    private fun startRender(frameIndex: Int) {
        val target = backBitmap
        if (EmojiPlaybackDiagnostics.enabled) diagnosticRenderStage = "queued"
        // Registra il Job prima di avviarlo: con Main.immediate un rendering
        // rapido può terminare prima che launch restituisca, lasciando altrimenti
        // un Job già completato in renderJob e bloccando tutti i frame successivi.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                var result: AnimationFrameRenderer.Result
                // Anche renderSync nativo può restituire BUSY: ritentiamo per un tempo limitato.
                var busyRetries = 0
                do {
                    if (EmojiPlaybackDiagnostics.enabled) diagnosticRenderStage = "queued"
                    result = withContext(RLottieRenderPool) {
                        if (EmojiPlaybackDiagnostics.enabled) diagnosticRenderStage = "native-or-lock"
                        AnimationFrameRenderer.render(animation, frameIndex, target).also {
                            if (EmojiPlaybackDiagnostics.enabled) diagnosticRenderStage = "awaiting-main"
                        }
                    }
                    if (EmojiPlaybackDiagnostics.enabled && result != AnimationFrameRenderer.Result.Success) {
                        diagnosticLastFailure = result.toString()
                    }
                    val rendererIsBusy = result is AnimationFrameRenderer.Result.NativeFailure &&
                            result.code == RLottieLib.RENDER_BUSY
                    if (!rendererIsBusy || busyRetries >= MAX_BUSY_RETRIES) break
                    busyRetries += 1
                    if (EmojiPlaybackDiagnostics.enabled) diagnosticRenderStage = "busy-retry"
                    delay(BUSY_RETRY_DELAY_MS)
                } while (isActive && !disposed)

                // Dopo una pausa, scarta il frame che era ancora in lavorazione.
                val mayPublish = playing || frameIndex == requestedFrame
                if (isActive &&
                    !disposed &&
                    mayPublish &&
                    result == AnimationFrameRenderer.Result.Success
                ) {
                    val previousFront = frontBitmap
                    frontBitmap = backBitmap
                    backBitmap = previousFront

                    publishedFrame = frameIndex
                    if (EmojiPlaybackDiagnostics.enabled) diagnosticPublishedFrames += 1

                    onFrameReady(frameIndex)
                }
            } finally {
                renderJob = null
                if (EmojiPlaybackDiagnostics.enabled) diagnosticRenderStage = "idle"

                // Recupera l'ultima richiesta arrivata durante il rendering.
                if (!disposed && requestedFrame >= 0 && requestedFrame != publishedFrame &&
                    (!isActive || requestedFrame != frameIndex)) {
                    startRender(requestedFrame)
                }
            }
        }
        renderJob = job
        job.start()
    }

    suspend fun close() {
        if (disposed) return
        disposed = true
        playing = false
        // Il codice nativo deve terminare prima che le bitmap tornino al pool.
        renderJob?.cancelAndJoin()
        renderJob = null
        withContext(Dispatchers.Default) {
            animation.close()
        }
        EmojiBitmapPool.release(frontBitmap)
        EmojiBitmapPool.release(backBitmap)
    }

    private companion object {
        const val MAX_BUSY_RETRIES = 3
        const val BUSY_RETRY_DELAY_MS = 8L
    }
}
