package com.axoo.engine.emoji.text

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.widget.TextView
import com.axoo.engine.lottie.LottieHost
import com.axoo.engine.emoji.catalog.EmojiData
import kotlin.math.roundToInt

/**
 * TextView di sola lettura (Feed, Commenti).
 * Essendo all'interno di un RecyclerView, non si preoccupa dello scroll interno:
 * accende le animazioni quando appare su schermo, e le spegne quando esce.
 */
class AnimatedEmojiTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextView(context, attrs), LottieHost {
    
    // Il nostro pittore personale
    private val renderer = AnimatedEmojiRenderer(
        context = context.applicationContext,
        hostView = this,
    )
    private var playbackAllowed = true

    fun setPlaybackAllowed(value: Boolean) {
        playbackAllowed = value
        renderer.setAnimating(value && isAttachedToWindow)
    }

    fun dispose() {
        setPlaybackAllowed(false)
        renderer.clear()
    }

    override val isAttachedForLottie: Boolean
        get() = isAttachedToWindow

    override fun invalidateLottie(bounds: Rect?) {
        if (isAttachedToWindow) invalidate() // Richiesta di ridisegnare il fotogramma Lottie
    }

    fun setEmojiText(value: CharSequence) {
        text = value
        renderer.updateFromText(value)
    }

    /** Entities use Android UTF-16 offsets, as in posts and comments. */
    fun setEmojiText(value: String, entities: List<MessageEntity>) {
        val result = SpannableStringBuilder(value)
        var boundary = value.length
        for (entity in entities.sortedByDescending { it.offset }) {
            if (entity.type != MessageEntity.TYPE_CUSTOM_EMOJI || entity.offset < 0 ||
                entity.length <= 0 || entity.offset > boundary || entity.length > boundary - entity.offset) continue
            val emoji = EmojiData.byId[entity.customEmojiId] ?: continue
            val end = entity.offset + entity.length
            val fallback = value.substring(entity.offset, end)
            result.replace(entity.offset, end, "\uFFFC")
            result.setSpan(AnimatedEmojiSpan(
                renderSizePx = (textSize * 1.4f).roundToInt(),
                emojiIndex = emoji.id,
                emojiName = emoji.name,
                fallbackChar = fallback,
            ), entity.offset, entity.offset + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            boundary = entity.offset
        }
        setEmojiText(result)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // La View è entrata nello schermo: prepariamo le emoji e accendiamo i motori
        renderer.updateFromText(text)
        renderer.setAnimating(playbackAllowed)
    }

    override fun onDetachedFromWindow() {
        // La View è uscita dallo schermo (scartata dal RecyclerView): spegniamo tutto per risparmiare batteria
        renderer.setAnimating(false)
        renderer.clear()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        // Ellipsized spans must not retain coordinates from a previous layout.
        (text as? Spanned)?.getSpans(0, text.length, AnimatedEmojiSpan::class.java)
            ?.forEach { it.spanDrawn = false }
        // Pre-calcolo delle posizioni
        renderer.beforeTextDraw(layout, text)
        
        // Android disegna il testo normale (con i buchi trasparenti per le emoji)
        super.onDraw(canvas)
        
        if (renderer.isEmpty) return

        // Nessun calcolo complesso per lo scroll qui: diciamo al pittore di considerare l'intera altezza
        renderer.setBounds(0f, height.toFloat())
        
        // Spostiamo temporaneamente il "Laser" per superare il margine (Padding)
        canvas.save()
        canvas.translate(compoundPaddingLeft.toFloat(), extendedPaddingTop.toFloat())
        
        // Il pittore disegna le emoji esattamente nei buchi
        renderer.draw(canvas)
        
        // Rimettiamo a posto il Laser
        canvas.restore()
    }
}
