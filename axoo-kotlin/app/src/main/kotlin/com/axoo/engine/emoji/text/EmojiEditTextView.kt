package com.axoo.engine.emoji.text

import android.content.Context
import android.graphics.Canvas
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import com.axoo.engine.lottie.LottieHost

/**
 * Campo di testo che sa disegnare le emoji animate.
 * Lascia fare il testo standard ad Android, e poi sposta il Canvas
 * per far disegnare le Lottie al nostro Pittore (AnimatedEmojiRenderer).
 */
class EmojiEditTextView(context: Context) : EditText(context), LottieHost {

    var emojiRenderer: AnimatedEmojiRenderer? = null
    var onEmojiAwareBackspace: (() -> Boolean)? = null
    var onSelectionChangedListener: (() -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangedListener?.invoke()
    }

    override val isAttachedForLottie: Boolean
        get() = isAttachedToWindow

    override fun invalidateLottie(bounds: android.graphics.Rect?) {
        if (!isAttachedForLottie) return
        invalidate() // Il pittore dice che c'è un nuovo fotogramma, ridisegniamo tutto!
    }

    /**
     * Posto di blocco per la tastiera a schermo (es. Gboard).
     */
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(base, true) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                // Se stiamo cancellando un'emoji intera, fermiamo la tastiera standard per evitare di spezzarla.
                if (consumeEmojiBackspace(beforeLength, afterLength)) return true
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        // 1. Android disegna il testo normale (lasciando buchi vuoti dove ci sono gli Span)
        super.onDraw(canvas)

        val renderer = emojiRenderer ?: return
        if (renderer.isEmpty) return

        // 2. Calcola la finestra visibile (viewport) per ignorare le emoji fuori schermo e risparmiare un sacco di batteria
        val offsetYRaw = textLayoutOffsetYRaw()
        val viewportTop = scrollY.toFloat() - offsetYRaw
        val viewportBottom = viewportTop + height.toFloat()
        renderer.setBounds(viewportTop, viewportBottom) //queste sono le dimensioni dello schermo (viewport) che utente vede

        // 3. Sposta il "Laser" (Canvas) facendolo slittare sui margini del testo
        canvas.save() // Memorizza la posizione originale del laser
        canvas.translate(textLayoutOffsetX(), offsetYRaw - scrollY.toFloat()) // Scende giù fino al testo

        // 4. Il pittore ora può disegnare ai suoi "Zeri" e colpirà i buchi perfetti
        renderer.draw(canvas)

        // 5. Rimettiamo a posto il laser per non sfasare il resto del sistema
        canvas.restore()
    }

    // Calcola di quanti pixel spostare il laser verso destra (Padding - Scroll)
    private fun textLayoutOffsetX(): Float = compoundPaddingLeft.toFloat() - scrollX

    // Calcola di quanti pixel spostare il laser in basso (Padding + Gravità del testo)
    private fun textLayoutOffsetYRaw(scrollYForDraw: Int = scrollY): Float {
        val textLayout = layout ?: return extendedPaddingTop.toFloat()
        val availableHeight = height - compoundPaddingTop - compoundPaddingBottom
        val verticalGravity = gravity and Gravity.VERTICAL_GRAVITY_MASK
        val verticalOffset = when (verticalGravity) {
            Gravity.BOTTOM -> availableHeight - textLayout.height + scrollYForDraw
            Gravity.CENTER_VERTICAL -> (availableHeight - textLayout.height) / 2
            else -> 0
        }.coerceAtLeast(0)
        return (extendedPaddingTop + verticalOffset).toFloat()
    }

    private fun consumeEmojiBackspace(beforeLength: Int, afterLength: Int): Boolean =
        beforeLength > 0 && afterLength == 0 && onEmojiAwareBackspace?.invoke() == true
}
