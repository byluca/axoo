package com.axoo.engine.emoji.animation.rendering

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.os.Looper
import android.os.SystemClock
import com.axoo.engine.emoji.animation.model.AnimatedEmojiKey
import com.axoo.engine.emoji.animation.time.PlaybackPolicy
import com.axoo.engine.lottie.LottieFrameClock
import com.axoo.engine.lottie.LottieHost
import com.axoo.engine.lottie.RLottieAnimation
import com.axoo.engine.lottie.RLottieNativeLoader
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Carica il TGS solo quando viene richiesta la riproduzione.
 * Il contatore generation scarta i caricamenti superati da una nuova richiesta.
 * L'attach dell'host e la richiesta di play sono stati distinti.
 */
class ImageReceiver(
    context: Context,
    private val host: LottieHost,
    private val playbackKey: String? = null,
    private val onAnimationFrame: (() -> Unit)? = null,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()

    private var requestedAsset: AnimatedEmojiKey? = null
    private var targetWidth = 0
    private var targetHeight = 0
    private var attached = false
    private var playRequested = false

    private var generation = 0L
    private var hasPublishedFrame = false

    private var loadJob: Job? = null
    private var diagnosticsJob: Job? = null
    private var player: LottiePlayer? = null

    internal val isAnimationLoaded: Boolean
        get() = player != null

    internal val isLoadingAnimation: Boolean
        get() = loadJob != null

    val hasDrawableContent: Boolean
        get() = player?.currentBitmap != null

    val alpha: Int
        get() = paint.alpha

    private val frameListener = LottieFrameClock.Listener { frameTimeNanos ->
        val activePlayer = player
        if (canDrivePlayback() && activePlayer != null) {
            activePlayer.onVsync(frameTimeNanos)
        }
    }

    fun setImage(asset: AnimatedEmojiKey?) {
        checkMainThread()
        if (requestedAsset == asset) return
        requestedAsset = asset
        rebuildPlayer()
    }

    fun setSize(widthPx: Int, heightPx: Int) {
        checkMainThread()
        val safeWidth = widthPx.coerceAtLeast(0)
        val safeHeight = heightPx.coerceAtLeast(0)
        if (targetWidth == safeWidth && targetHeight == safeHeight) return
        targetWidth = safeWidth
        targetHeight = safeHeight
        rebuildPlayer()
    }

    fun onAttachedToWindow() {
        checkMainThread()
        if (attached) return
        attached = true
        startLoadIfReady()
        if (playRequested) player?.play()
        updateFrameClockRegistration()
    }

    fun onDetachedFromWindow() {
        checkMainThread()
        if (!attached) return
        attached = false
        cancelAnimationLoad()
        player?.suspend()
        updateFrameClockRegistration()
    }

    fun play() {
        checkMainThread()
        playRequested = true
        startLoadIfReady()
        if (canDrivePlayback()) player?.play()
        updateFrameClockRegistration()
    }

    fun pause() {
        checkMainThread()
        playRequested = false
        cancelAnimationLoad()
        player?.pause()
        updateFrameClockRegistration()
    }

    fun setAlpha(alpha: Int) {
        checkMainThread()
        paint.alpha = alpha.coerceIn(0, 255)
    }

    fun setColorFilter(colorFilter: ColorFilter?) {
        checkMainThread()
        paint.colorFilter = colorFilter
    }

    /**
     * Disegna esclusivamente la bitmap front già pronta.
     * Nessun parsing o rendering C++ avviene in questo metodo.
     */
    fun draw(canvas: Canvas, bounds: RectF) {
        checkMainThread()
        val bitmap = player?.currentBitmap ?: return
        if (bounds.width() <= 0f || bounds.height() <= 0f) return

        val scale = min(bounds.width() / bitmap.width, bounds.height() / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = bounds.left + (bounds.width() - drawWidth) / 2f
        val top = bounds.top + (bounds.height() - drawHeight) / 2f
        destination.set(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(bitmap, null, destination, paint)
    }

    private fun rebuildPlayer() {
        releasePlayer()
        hasPublishedFrame = false
        startLoadIfReady()
    }

    private fun startLoadIfReady() {
        val asset = requestedAsset ?: return
        if (!playRequested || !attached ||
            !host.isAttachedForLottie ||
            targetWidth <= 0 ||
            targetHeight <= 0 ||
            loadJob != null ||
            player != null
        ) {
            return
        }
        if (loadJob != null || player != null) return
        val requestGeneration = generation

        val job = scope.launch(start = CoroutineStart.LAZY) {
            var loaded: RLottieAnimation? = null
            try {
                for (attempt in 0 until LOAD_ATTEMPTS) {
                    loaded = RLottieNativeLoader.load(asset.path, appContext)
                    if (loaded != null) break
                    if (attempt < LOAD_ATTEMPTS - 1) {
                        delay(RETRY_DELAY_MS * (attempt + 1))
                    }
                }
                if (!isActive || requestGeneration != generation) {
                    return@launch
                }

                val animation = loaded ?: return@launch
                val newPlayer = LottiePlayer(
                    animation = animation,
                    widthPx = targetWidth,
                    heightPx = targetHeight,
                    scope = scope,
                    playbackKey = playbackKey,
                    maximumFrameRate = PlaybackPolicy.maximumFrameRate(
                        sizePx = maxOf(targetWidth, targetHeight),
                        density = appContext.resources.displayMetrics.density,
                    ),
                    onFrameReady = {
                        if (attached && host.isAttachedForLottie) {
                            val suppliesPoster = !hasPublishedFrame
                            hasPublishedFrame = true
                            if (onAnimationFrame != null && !suppliesPoster) onAnimationFrame.invoke()
                            else host.invalidateLottie()
                        }
                    },
                )

                loaded = null
                player = newPlayer
                newPlayer.renderInitialFrame()
                if (playRequested && attached && host.isAttachedForLottie) {
                    newPlayer.play()
                    updateFrameClockRegistration()
                }
            } finally {
                loaded?.let(::releaseAnimation)
                if (requestGeneration == generation) {
                    loadJob = null
                    if (!isActive) startLoadIfReady()
                }
            }
        }
        loadJob = job
        job.start()
        updateDiagnostics()
    }

    private fun cancelAnimationLoad() {
        loadJob?.cancel()
        // Completion clears the slot before another native load can start.
    }

    private fun releasePlayer() {
        stopDiagnostics("release")
        // Ogni cambio di asset o dimensione invalida anche i caricamenti in corso.
        generation += 1
        LottieFrameClock.unregister(frameListener)
        loadJob?.cancel()
        loadJob = null

        val oldPlayer = player
        player = null
        if (oldPlayer != null) {
            scope.launch { oldPlayer.close() }
        }
        if (host.isAttachedForLottie) {
            host.invalidateLottie()
        }
    }

    private fun releaseAnimation(animation: RLottieAnimation) {
        scope.launch(Dispatchers.Default) { animation.close() }
    }

    private fun canDrivePlayback(): Boolean =
        attached && host.isAttachedForLottie && playRequested

    private fun updateFrameClockRegistration() {
        if (canDrivePlayback() && player != null) {
            LottieFrameClock.register(frameListener)
        } else {
            LottieFrameClock.unregister(frameListener)
        }
        updateDiagnostics()
    }

    private fun updateDiagnostics() {
        if (!EmojiPlaybackDiagnostics.enabled) return
        if (!canDrivePlayback()) {
            stopDiagnostics("pause")
            return
        }
        if (diagnosticsJob != null) return
        logDiagnostics("play")
        diagnosticsJob = scope.launch {
            val tracker = EmojiPlaybackStallTracker()
            while (isActive) {
                val activePlayer = player
                val event = tracker.sample(
                    active = canDrivePlayback() && LottieFrameClock.isInForeground &&
                        activePlayer?.totalFrames != 1,
                    progress = activePlayer?.diagnosticPublishedFrames ?: 0L,
                    nowMs = SystemClock.uptimeMillis(),
                )
                if (event != null) logDiagnostics(event.name)
                delay(2_000L)
            }
        }
    }

    private fun stopDiagnostics(reason: String) {
        if (diagnosticsJob == null) return
        diagnosticsJob?.cancel()
        diagnosticsJob = null
        logDiagnostics(reason)
    }

    private fun logDiagnostics(event: String) {
        EmojiPlaybackDiagnostics.log(
            "$event receiver=${System.identityHashCode(this)} emoji=${requestedAsset?.id} " +
                "size=${targetWidth}x$targetHeight generation=$generation attached=$attached " +
                "hostAttached=${host.isAttachedForLottie} playRequested=$playRequested " +
                "loading=${loadJob?.isActive == true} " +
                "${LottieFrameClock.diagnosticState(frameListener)} " +
                (player?.diagnosticState() ?: "player=missing"),
        )
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "ImageReceiver must be changed or drawn from the main thread"
        }
    }

    private companion object {
        const val LOAD_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 150L
    }
}
