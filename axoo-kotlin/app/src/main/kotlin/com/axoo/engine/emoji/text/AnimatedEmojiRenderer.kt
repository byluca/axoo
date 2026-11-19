package com.axoo.engine.emoji.text

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.text.Layout
import android.text.Spanned
import android.view.View
import com.axoo.engine.emoji.animation.drawable.AnimatedEmojiLease
import com.axoo.engine.emoji.animation.drawable.AnimatedEmojiRegistry
import com.axoo.engine.emoji.animation.model.AnimatedEmojiKey
import com.axoo.engine.emoji.animation.model.EmojiRasterSizePolicy
import com.axoo.engine.emoji.catalog.animatedEmojiAssetPath
import com.axoo.engine.emoji.list.EmojiFrameCoordinator
import com.axoo.engine.lottie.LottieHost

/**
 * AnimatedEmojiRenderer disegna le emoji animate (Lottie) nei "buchi" lasciati da AnimatedEmojiSpan.
 *
 * Flusso:
 * 1. beforeTextDraw: Scansiona il testo per trovare gli span. Per ognuno, usa 'getOrPut'
 *    per creare o riciclare uno 'SharedDrawable' ( che gestisce l'animazione Lottie).
 * 2. draw: Android lo chiama 60 volte al sec. Il Renderer ignora il testo e scorre la sua
 *    LinkedHashMap 'holders'. Legge le coordinate (X,Y) calcolate dallo Span e "timbra"
 *    l'animazione sullo schermo limitando la concorrenza (MAX_CONCURRENT_ANIMATIONS).
 */
class AnimatedEmojiRenderer(
    private val context: Context,
    private val hostView: View,
    private val directInvalidate: () -> Unit = { hostView.invalidate() },
) {

    private val holders = linkedMapOf<AnimatedEmojiSpan, Holder>()
    private val drawablesByKey = mutableMapOf<DrawableKey, SharedDrawable>()
    private val density = context.resources.displayMetrics.density
    private var animating = false
    private var released = false
    private var frameCoordinator: EmojiFrameCoordinator? = null

    // Snapshot per il caching del layout.
    private var lastLayoutRef: Layout? = null
    private var lastLayoutHeight = -1
    private var lastLayoutLineCount = -1
    private var lastTextLength = -1
    private var lastTextIdentity = 0

    // Culling viewport.
    private var boundsValid = false
    private var boundTop = 0f
    private var boundBottom = 0f

    private val invalidate: () -> Unit = {

        val parent = hostView as? LottieHost
        val hostAttached = parent?.isAttachedForLottie ?: hostView.isAttachedToWindow
        if (hostAttached) {
            if (parent != null) {
                parent.invalidateLottie()
            } else {
                val coordinator = frameCoordinator
                if (coordinator != null) {
                    coordinator.requestInvalidate(hostView)
                } else {
                    directInvalidate()
                }
            }
        }
    }

    fun setFrameCoordinator(coordinator: EmojiFrameCoordinator?) {
        if (frameCoordinator === coordinator) return
        frameCoordinator?.cancel(hostView)
        frameCoordinator = coordinator
    }

    val isEmpty: Boolean
        get() = holders.isEmpty()

    val isPlaying: Boolean
        get() = animating && holders.isNotEmpty()

    fun updateFromText(text: CharSequence?) {
        val spans = if (text is Spanned && text.isNotEmpty()) {
            text.getSpans(0, text.length, AnimatedEmojiSpan::class.java).toList()
        } else {
            emptyList()
        }
        update(spans)
    }

    private fun update(spans: Collection<AnimatedEmojiSpan>) {
        if (released) return

        val active = spans.toSet()
        val iterator = holders.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in active) {
                releaseHolder(entry.value)
                iterator.remove()
            }
        }

        for (span in spans) {
            val name = span.emojiName?.takeIf { it.isNotBlank() } ?: continue
            if (holders.containsKey(span)) continue
            holders[span] = createHolder(span, name)
        }

        drawablesByKey.values.forEach { it.setAnimatingIntent(animating) }
    }

    /**
     * Imposta il viewport verticale nelle coordinate del Layout prima di draw().
     * Senza limiti validi, tutte le posizioni degli span sono considerate.
     */
    fun setBounds(top: Float, bottom: Float) {
        boundsValid = bottom > top
        boundTop = top
        boundBottom = bottom
    }

    /**
     * Da chiamare prima di super.onDraw(). Conserva le posizioni esistenti:
     * DynamicLayout può ridisegnare solo le righe modificate.
     */
    fun beforeTextDraw(layout: Layout?, text: CharSequence?) {
        if (released) return
        val height = layout?.height ?: -1
        val lineCount = layout?.lineCount ?: -1
        val textLength = text?.length ?: -1
        val textIdentity = if (text != null) System.identityHashCode(text) else 0

        val layoutChanged = layout !== lastLayoutRef ||
            height != lastLayoutHeight ||
            lineCount != lastLayoutLineCount
        val textChanged = textLength != lastTextLength || textIdentity != lastTextIdentity

        if (!layoutChanged && !textChanged) return

        lastLayoutRef = layout
        lastLayoutHeight = height
        lastLayoutLineCount = lineCount
        lastTextLength = textLength
        lastTextIdentity = textIdentity

        if (textChanged) {
            updateFromText(text)
        }
    }

    fun draw(canvas: Canvas) {

        for (shared in drawablesByKey.values) {
            shared.resetFrameVisibility()
        }
        for ((span, holder) in holders) {
            if (!span.spanDrawn) continue
            if (boundsValid) {

                val margin = span.renderSizePx.toFloat()
                if (span.lastDrawnCy < boundTop - margin) continue
                if (span.lastDrawnCy > boundBottom + margin) continue
            }
            holder.shared.recordVisible(span.lastDrawnCx, span.lastDrawnCy)
            val half = span.renderSizePx / 2f
            holder.bounds.set(
                (span.lastDrawnCx - half).toInt(),
                (span.lastDrawnCy - half).toInt(),
                (span.lastDrawnCx + half).toInt(),
                (span.lastDrawnCy + half).toInt(),
            )
            val drawable = holder.shared.drawable
            if (drawable.hasDrawableContent) {
                drawable.draw(canvas, holder.bounds)
            }
        }

        allowAnimatedVisibleDrawables(MAX_CONCURRENT_ANIMATIONS)
        for (shared in drawablesByKey.values) {
            shared.applyState()
        }
    }

    private fun allowAnimatedVisibleDrawables(maxConcurrent: Int) {
        repeat(maxConcurrent) {
            var best: SharedDrawable? = null
            for (candidate in drawablesByKey.values) {
                if (!candidate.isPendingVisibleCandidate()) continue
                if (best == null || candidate.isVisuallyBefore(best)) {
                    best = candidate
                }
            }
            best?.setAnimationAllowed(true) ?: return
        }
    }

    fun setAnimating(value: Boolean) {
        if (released) return
        refreshHostAttachment()
        if (animating == value) return
        animating = value
        for (shared in drawablesByKey.values) {
            shared.setAnimatingIntent(value)
            if (!value) {
                // Se l'animazione viene interrotta (es. scroll veloce o view nascosta), 
                // forziamo la pausa immediata per risparmiare batteria.
                shared.resetFrameVisibility()
                shared.applyState()
            }
        }
        if (value) {

            invalidate()
        }
    }

    fun refreshHostAttachment() {
        if (released) return
        drawablesByKey.values.forEach { it.refreshHostAttachment() }
    }

    fun clear() {
        holders.values.forEach { releaseHolder(it) }
        holders.clear()
        drawablesByKey.clear()

        lastLayoutRef = null
        lastLayoutHeight = -1
        lastLayoutLineCount = -1
        lastTextLength = -1
        lastTextIdentity = 0
    }

    fun release() {
        if (released) return
        released = true
        clear()
    }

    private fun createHolder(
        span: AnimatedEmojiSpan,
        name: String,
    ): Holder {
        val key = DrawableKey(
            name = name,
            sizePx = EmojiRasterSizePolicy.inlineRasterSizePx(span.renderSizePx, density),
        )
        val shared = drawablesByKey.getOrPut(key) {
            SharedDrawable(
                context = context.applicationContext,
                key = key,
                isAttached = {
                    val parent = hostView as? LottieHost
                    parent?.isAttachedForLottie ?: hostView.isAttachedToWindow
                },
                invalidate = invalidate,
            ).also { it.load() }
        }
        shared.refCount += 1
        shared.setAnimatingIntent(animating)
        return Holder(shared)
    }

    private fun releaseHolder(holder: Holder) {
        val shared = holder.shared
        shared.refCount = (shared.refCount - 1).coerceAtLeast(0)
        if (shared.refCount == 0) {
            drawablesByKey.remove(shared.key)
            shared.dispose()
        }
    }

    private data class DrawableKey(
        val name: String,
        val sizePx: Int,
    )

    // Scatola che unisce l'animazione condivisa (SharedDrawable) al
    // rettangolo esatto (Rect) in cui dovrà essere disegnata sullo schermo.
    private class Holder(
        val shared: SharedDrawable,
        val bounds: Rect = Rect(),
    )

    private class SharedDrawable(
        private val context: Context,
        val key: DrawableKey,
        private val isAttached: () -> Boolean,
        private val invalidate: () -> Unit,
    ) {
        var refCount: Int = 0
        var visibleCount: Int = 0
            private set
        private var firstX: Float = Float.POSITIVE_INFINITY
        private var firstY: Float = Float.POSITIVE_INFINITY
        private var disposed = false
        private var animatingIntent = false
        private var animationAllowed = false
        private var lastShouldPlay = false
        private val host = object : LottieHost {
            override val isAttachedForLottie: Boolean
                get() = isAttached()

            override fun invalidateLottie(bounds: Rect?) {
                invalidate()
            }
        }
        private val lease: AnimatedEmojiLease = AnimatedEmojiRegistry.acquire(
            context = context,
            key = AnimatedEmojiKey(
                id = key.name,
                path = animatedEmojiAssetPath(key.name),
                sizePx = key.sizePx.coerceAtLeast(1),
            ),
            host = host,
        )
        val drawable = lease.drawable

        fun load() {
            if (disposed) return
            refreshHostAttachment()
            lease.pause()
            invalidate()
            applyState()
        }

        fun refreshHostAttachment() {
            if (!disposed) drawable.refreshHostAttachment()
        }

        fun setAnimatingIntent(value: Boolean) {
            if (disposed) return
            animatingIntent = value
        }

        fun setAnimationAllowed(value: Boolean) {
            if (disposed) return
            animationAllowed = value
        }

        fun resetFrameVisibility() {
            visibleCount = 0
            firstX = Float.POSITIVE_INFINITY
            firstY = Float.POSITIVE_INFINITY
            animationAllowed = false
        }

        fun recordVisible(cx: Float, cy: Float) {
            if (visibleCount == 0 || cy < firstY || (cy == firstY && cx < firstX)) {
                firstX = cx
                firstY = cy
            }
            visibleCount += 1
        }

        fun isPendingVisibleCandidate(): Boolean =
            visibleCount > 0 && !animationAllowed

        fun isVisuallyBefore(other: SharedDrawable): Boolean {
            val vertical = firstY.compareTo(other.firstY)
            return vertical < 0 || (vertical == 0 && firstX.compareTo(other.firstX) < 0)
        }

        fun applyState() {
            if (disposed) return
            val shouldPlay =
                animatingIntent &&
                    visibleCount > 0 &&
                    animationAllowed
            if (lastShouldPlay == shouldPlay) return
            lastShouldPlay = shouldPlay
            if (shouldPlay) {
                drawable.refreshHostAttachment()
                lease.play()
            } else {
                lease.pause()
            }
        }

        fun dispose() {
            if (disposed) return
            disposed = true
            animatingIntent = false
            animationAllowed = false
            lastShouldPlay = false
            lease.close()
        }
    }

    private companion object {
        const val MAX_CONCURRENT_ANIMATIONS = 50
    }
}
