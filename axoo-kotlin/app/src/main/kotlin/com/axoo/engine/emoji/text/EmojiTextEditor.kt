package com.axoo.engine.emoji.text

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.InputFilter
import android.text.Spannable
import android.text.Spanned
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import com.axoo.engine.emoji.catalog.Emoji
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.interTypeface
import kotlin.math.max
import kotlin.math.min

class EmojiTextEditor(
    private val context: Context,
    emojis: List<Emoji>,
    initialText: String = "",
    initialEntities: List<MessageEntity> = emptyList(),
    fontSizeSp: Float = 16f,
    fontWeight: Int = 400,
    fontFamily: String? = null,
    hintText: String = "",
    textColor: Int = NativeColors.textPrimary,
    cursorColor: Int = NativeColors.accent,
    maxLines: Int = 4,
    lineSpacingMultiplier: Float = 1f, // Interlinea (1 = standard)
    private val editGravity: Int = Gravity.START or Gravity.TOP, // Allineamento del testo (es. in alto a sx)
    private val verticalPaddingPx: Int = 0, // Spazio interno sopra e sotto (in pixel)
    private val autoBringPointIntoView: Boolean = true, // Autoscroll per mantenere il cursore visibile
    private val onSelectionChanged: ((Int, Int) -> Unit)? = null,
    private val onTextChanged: (String, List<MessageEntity>) -> Unit, // Callback ad ogni modifica: dà testo e posizione emoji
) {
    private val editText = EmojiEditTextView(context)
    // Dizionario veloce: crea una Map in cui la chiave è ID e il valore è l'Emoji, per trovarle all'istante (ID->EMOJI)
    private val emojiById = emojis.associateBy { it.id }
    private val emojiRenderer = AnimatedEmojiRenderer(
        context = context,
        hostView = editText,
    )

    private var applyingInternalChange = false
    private var disposed = false
    private var active = true
    private var playbackGate: EmojiPlaybackGate = EmojiPlaybackGate.FullyVisible
    private var lastPublishedText = ""
    private var lastPublishedEntities = emptyList<MessageEntity>()
    private var pendingExternalBind: PendingExternalBind? = null // Aggiornamento esterno in attesa per non interrompere la digitazione
    private var lastSelectionStart = 0 // Memoria della posizione inizio del cursore
    private var lastSelectionEnd = 0 // Memoria della posizione fine del cursore

    // Android may move the cursor before TextWatcher has published the new text.
    // Notify after the edit finishes, including moves that don't change the text.
    private val publishSelection = Runnable {
        if (!disposed) onSelectionChanged?.invoke(editText.selectionStart, editText.selectionEnd)
    }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit //non fare niente (siamo obbligati a fare override di questi 3 metodi quando implementiamo TextWatcher)
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            rememberSelection()
            if (!applyingInternalChange && !disposed) {
                publishState()
                applyPendingExternalBindIfReady()
            }
        }
    }

    private val attachStateListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            syncAnimationState()
        }

        override fun onViewDetachedFromWindow(v: View) {
            syncAnimationState()
        }
    }

    //costruttore
    init {
        if (onSelectionChanged != null) {
            editText.onSelectionChangedListener = {
                editText.removeCallbacks(publishSelection)
                editText.post(publishSelection)
            }
        }
        editText.emojiRenderer = emojiRenderer
        editText.onEmojiAwareBackspace = {
            if (hasComposingText(editText.text)) {
                false
            } else {
                deleteEmojiAwareBackspace(
                    clearComposingText = false,
                    allowPlainTextDeletion = false,
                )
            }
        }
        editText.apply {
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, verticalPaddingPx, 0, verticalPaddingPx)
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
            typeface = resolveTypeface(fontFamily, fontWeight)
            includeFontPadding = false
            setLineSpacing(0f, lineSpacingMultiplier)
            setSingleLine(false)
            minLines = 1
            this.maxLines = maxLines
            setMinHeight(0)
            minimumHeight = 0
            gravity = editGravity
            hint = hintText
            highlightColor = NativeColors.textSelection
            setHorizontallyScrolling(false)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = filters + InputFilter { source, start, end, dest, destStart, destEnd ->
                if (applyingInternalChange) {
                    null
                } else {
                    val incoming = animatedEmojiCount(source, start, end)
                    val remaining = animatedEmojiCount(dest) - animatedEmojiCount(dest, destStart, destEnd)
                    if (incoming == 0 || remaining + incoming <= MAX_ANIMATED_EMOJI) {
                        null
                    } else {
                        // Reject the paste without deleting the selected text or emoji spans.
                        dest.subSequence(destStart, destEnd)
                    }
                }
            }
            addTextChangedListener(textWatcher)
            addOnAttachStateChangeListener(attachStateListener)
        }

        if (initialText.isNotEmpty() || initialEntities.isNotEmpty()) {
            setTextValue(initialText, initialEntities)
        }
    }

    // Restituisce la View grafica di Android per stamparla sullo schermo.
    fun getView(): View = editText

    // Sposta forzatamente il cursore alla fine del testo.
    fun moveCursorToEnd() {
        moveCursorTo(editText.text?.length ?: 0)
    }

    fun moveCursorTo(offset: Int) {
        editText.setSelection(offset.coerceIn(0, editText.text?.length ?: 0))
        rememberSelection()
    }

    /** An explicit picker selection commits the IME word before replacing it. */
    fun finishComposition() {
        pendingExternalBind = null
        editText.clearComposingText()
        (editText.text as? Spannable)?.let(BaseInputConnection::removeComposingSpans)
    }

    // Sostituisce tutto il testo e le emoji con un nuovo contenuto.
    fun setTextValue(text: String, entities: List<MessageEntity>) {
        val authoritativeClear = text.isEmpty() && entities.isEmpty()
        if (authoritativeClear) {
            pendingExternalBind = null
            editText.clearComposingText()
            // Se possibile, passa in modo sicuro il testo alla funzione che ne rimuove fisicamente le sottolineature.
            (editText.text as? Spannable)?.let(BaseInputConnection::removeComposingSpans)
        }

        if (
            text == lastPublishedText &&
            entities == lastPublishedEntities &&
            (!authoritativeClear || editText.text.isNullOrEmpty())
        ) {
            return
        }

        val editable = editText.text
        if (
            !authoritativeClear &&
            editText.hasFocus() &&
            hasComposingText(editable) &&
            !applyingInternalChange
        ) {
            pendingExternalBind = PendingExternalBind(text, entities)
            return
        }

        val currentText = editText.text
        val nativeState = EmojiTextMapper.toEditorState(
            text = text,
            entities = entities,
            emojiById = emojiById,
        )

        val selection = currentSelectionForRestore(currentText?.length ?: 0)

        emojiRenderer.clear()
        applyingInternalChange = true
        try {
            editText.setText(nativeState.text)
            val currentLength = editText.text?.length ?: 0
            editText.setSelection(
                selection.first.coerceIn(0, currentLength),
                selection.second.coerceIn(0, currentLength),
            )
        } finally {
            applyingInternalChange = false
        }
        applySpans(nativeState.entities)
        lastPublishedText = text
        lastPublishedEntities = entities
        rememberSelection()
        if (authoritativeClear) {
            context.getSystemService(InputMethodManager::class.java)?.restartInput(editText)
        }
    }

    // Inserisce un'emoji animata nell'esatto punto del cursore.
    fun insertEmoji(id: Int) {
        val emoji = emojiById[id] ?: return
        val editable = editText.text as? Editable ?: return
        val selection = currentSelectionForEdit(editable.length)
        val expandedRange = expandRangeForIntersectingEmojiSpans(
            editable = editable,
            start = selection.first,
            end = selection.second,
        )
        val start = expandedRange.start
        val end = expandedRange.end
        val remaining = animatedEmojiCount(editable) - animatedEmojiCount(editable, start, end)
        if (remaining + 1 > MAX_ANIMATED_EMOJI) return
        val fallback = emoji.fallback.ifBlank { EmojiTextMapper.OBJECT_REPLACEMENT_CHAR_STRING }
        val metrics = currentEmojiMetrics()

        applyingInternalChange = true
        try {
            editable.replace(start, end, fallback)

            val span = AnimatedEmojiSpan(
                renderSizePx = metrics.renderSizePx,
                fontMetrics = metrics.fontMetrics,
                emojiIndex = id,
                emojiName = emoji.name,
                fallbackChar = fallback,
            )
            val spanEnd = start + fallback.length
            editable.setSpan(span, start, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            editText.setSelection(spanEnd)
            editText.clearComposingText()
        } finally {
            applyingInternalChange = false
        }
        publishState()
        refreshEmojiLayoutAfterEdit()
    }

    // Inserisce testo normale o emoji standard nel punto del cursore.

    // Simula il tasto cancella per eliminare l'ultimo elemento prima del cursore.
    fun deleteLastChar() {
        deleteEmojiAwareBackspace(
            clearComposingText = true,
        )
    }

    // "Spegne" l'editor svuotando la memoria ed evitando rallentamenti.
    fun dispose() {
        if (disposed) return
        disposed = true
        editText.removeCallbacks(publishSelection)
        editText.onSelectionChangedListener = null
        editText.onEmojiAwareBackspace = null
        editText.removeTextChangedListener(textWatcher)
        editText.removeOnAttachStateChangeListener(attachStateListener)
        emojiRenderer.release()
    }

    // Accende o spegne il motore grafico delle animazioni.
    fun setActive(value: Boolean) {
        if (disposed || active == value) return
        active = value
        syncAnimationState()
    }

    // Mette in pausa o fa ripartire le animazioni (es. durante lo scroll).
    fun setEmojiPlaybackGate(gate: EmojiPlaybackGate) {
        if (disposed || playbackGate == gate) return
        playbackGate = gate
        syncAnimationState()
    }

    // Applica fisicamente i "bollini" grafici (le emoji animate) sul testo grezzo.
    private fun applySpans(entities: List<MessageEntity>) {
        val editable = editText.text as? Editable ?: return
        for (entity in entities.sortedBy(MessageEntity::offset)) {
            if (entity.type != MessageEntity.TYPE_CUSTOM_EMOJI) continue
            val start = entity.offset
            val end = start + entity.length
            if (start < 0 || end > editable.length || end <= start) continue
            val emoji = emojiById[entity.customEmojiId] ?: continue

            editable.getSpans(start, end, AnimatedEmojiSpan::class.java)
                .filter {
                    editable.getSpanStart(it) == start && editable.getSpanEnd(it) == end
                }
                .forEach(editable::removeSpan)

            val metrics = currentEmojiMetrics()
            editable.setSpan(
                AnimatedEmojiSpan(
                    renderSizePx = metrics.renderSizePx,
                    fontMetrics = metrics.fontMetrics,
                    emojiIndex = entity.customEmojiId,
                    emojiName = emoji.name,
                    fallbackChar = editable.substring(start, end),
                ),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        emojiRenderer.updateFromText(editable)
        syncAnimationState()
        refreshEmojiLayoutAfterEdit()
    }

    // Algoritmo che cancella un'emoji intera in un colpo solo.
    private fun deleteEmojiAwareBackspace(
        clearComposingText: Boolean,
        allowPlainTextDeletion: Boolean = true,
    ): Boolean {
        val editable = editText.text as? Editable ?: return false
        if (editable.isEmpty()) return false

        val rawSelectionStart = editText.selectionStart
        val rawSelectionEnd = editText.selectionEnd
        if (rawSelectionStart < 0 || rawSelectionEnd < 0) return false

        val selectionStart = min(rawSelectionStart, rawSelectionEnd).coerceIn(0, editable.length)
        val selectionEnd = max(rawSelectionStart, rawSelectionEnd).coerceIn(0, editable.length)
        if (selectionStart != selectionEnd) {
            if (
                !allowPlainTextDeletion &&
                !hasEmojiSpanIntersecting(editable, selectionStart, selectionEnd)
            ) {
                return false
            }
            val expandedRange = expandRangeForIntersectingEmojiSpans(
                editable = editable,
                start = selectionStart,
                end = selectionEnd,
            )
            return deleteRangeInternal(
                editable = editable,
                start = expandedRange.start,
                end = expandedRange.end,
                clearComposingText = clearComposingText,
            )
        }

        if (selectionStart == 0) return false

        findEmojiSpanForBackspace(editable, selectionStart)?.let { span ->
            val spanStart = editable.getSpanStart(span).coerceIn(0, editable.length)
            val spanEnd = editable.getSpanEnd(span).coerceIn(0, editable.length)
            return deleteRangeInternal(
                editable = editable,
                start = spanStart,
                end = spanEnd,
                clearComposingText = clearComposingText,
            )
        }

        // Input methods also use deleteSurroundingText for replacements and
        // autocorrection. If there is no custom emoji at the cursor, let the
        // original InputConnection honor the complete deletion request.
        if (!allowPlainTextDeletion) return false

        val charStart = EmojiTextBoundary.previousGraphemeStart(editable, selectionStart)
        return deleteRangeInternal(
            editable = editable,
            start = charStart,
            end = selectionStart,
            clearComposingText = clearComposingText,
        )
    }

    private fun animatedEmojiCount(
        text: CharSequence,
        start: Int = 0,
        end: Int = text.length,
    ): Int {
        if (text !is Spanned || start >= end) return 0
        return text.getSpans(start, end, AnimatedEmojiSpan::class.java).count { span ->
            val spanStart = text.getSpanStart(span)
            val spanEnd = text.getSpanEnd(span)
            spanStart >= 0 && spanEnd > spanStart && spanStart < end && spanEnd > start
        }
    }

    // Controlla se nel testo evidenziato c'è un'emoji animata.
    private fun hasEmojiSpanIntersecting(
        editable: Editable,
        start: Int,
        end: Int,
    ): Boolean = editable.getSpans(start, end, AnimatedEmojiSpan::class.java).any { span ->
        val spanStart = editable.getSpanStart(span)
        val spanEnd = editable.getSpanEnd(span)
        spanStart >= 0 && spanEnd > spanStart && spanStart < end && spanEnd > start
    }

    // Trova quale emoji stai cercando di cancellare dal cursore.
    private fun findEmojiSpanForBackspace(
        editable: Editable,
        cursor: Int,
    ): Any? {
        val searchStart = (cursor - 1).coerceAtLeast(0)
        return editable.getSpans(searchStart, cursor, AnimatedEmojiSpan::class.java)
            .firstOrNull { span ->
                val spanStart = editable.getSpanStart(span)
                val spanEnd = editable.getSpanEnd(span)
                spanStart >= 0 && spanEnd > spanStart && spanStart < cursor && spanEnd >= cursor
            }
    }

    // Allarga la selezione per includere per intero le emoji selezionate a metà.
    private fun expandRangeForIntersectingEmojiSpans(
        editable: Editable,
        start: Int,
        end: Int,
    ): DeleteRange {
        var expandedStart = start
        var expandedEnd = end
        editable.getSpans(start, end, AnimatedEmojiSpan::class.java).forEach { span ->
            val spanStart = editable.getSpanStart(span)
            val spanEnd = editable.getSpanEnd(span)
            if (spanStart < 0 || spanEnd <= spanStart) return@forEach
            if (spanStart >= end || spanEnd <= start) return@forEach
            expandedStart = min(expandedStart, spanStart)
            expandedEnd = max(expandedEnd, spanEnd)
        }
        return DeleteRange(
            start = expandedStart.coerceIn(0, editable.length),
            end = expandedEnd.coerceIn(0, editable.length),
        )
    }

    // Rimuove materialmente un pezzo di testo dal campo di scrittura.
    private fun deleteRangeInternal(
        editable: Editable,
        start: Int,
        end: Int,
        clearComposingText: Boolean = false,
    ): Boolean {
        if (start < 0 || end > editable.length || end <= start) return false
        applyingInternalChange = true
        try {
            editable.delete(start, end)
            editText.setSelection(start.coerceAtMost(editable.length))
            if (clearComposingText) {
                editText.clearComposingText()
            }
        } finally {
            applyingInternalChange = false
        }
        publishState()
        refreshEmojiLayoutAfterEdit()
        return true
    }

    // Legge il testo attuale e lo comunica all'app tramite onTextChanged.
    private fun publishState() {
        emojiRenderer.updateFromText(editText.text)
        val editable = editText.text ?: return onTextChanged("", emptyList())
        val spans = editable.getSpans(0, editable.length, AnimatedEmojiSpan::class.java).toList()
        val (text, entities) = EmojiTextMapper.extractCurrentState(
            text = editable,
            spans = spans,
            spanStart = { editable.getSpanStart(it) },
            spanEnd = { editable.getSpanEnd(it) },
            emojiById = emojiById,
        )
        lastPublishedText = text
        lastPublishedEntities = entities
        onTextChanged(text, entities)
    }

    // Ridisegna il box e scorre per tenere visibile il cursore.
    private fun refreshEmojiLayoutAfterEdit() {
        if (autoBringPointIntoView) {
            editText.bringPointIntoView(editText.selectionEnd)
        }
        editText.postInvalidateOnAnimation()
    }

    // Calcola se le animazioni devono muoversi o stare ferme.
    private fun syncAnimationState() {
        if (disposed) return
        emojiRenderer.setAnimating(
            active &&
                editText.isAttachedToWindow &&
                playbackGate.playbackAllowed,
        )
    }

    // Calcola la grandezza in pixel delle emoji in base al testo.
    private fun currentEmojiMetrics(): EmojiMetrics {
        val fm = editText.paint.fontMetricsInt
        val fontBox = (kotlin.math.abs(fm.descent) + kotlin.math.abs(fm.ascent)).coerceAtLeast(1)
        val measuredSize = (fontBox * COMPOSER_EMOJI_SCALE).toInt().coerceAtLeast(1)
        return EmojiMetrics(
            renderSizePx = measuredSize,
            fontMetrics = Paint.FontMetricsInt().also {
                it.ascent = fm.ascent
                it.descent = fm.descent
                it.top = fm.top
                it.bottom = fm.bottom
                it.leading = fm.leading
            },
        )
    }

    // Trova l'esatta posizione del cursore per inserire testo.
    private fun currentSelectionForEdit(textLength: Int): Pair<Int, Int> {
        val start = editText.selectionStart
        val end = editText.selectionEnd
        return if (start >= 0 && end >= 0) {
            min(start, end).coerceIn(0, textLength) to max(start, end).coerceIn(0, textLength)
        } else {
            lastSelectionStart.coerceIn(0, textLength) to lastSelectionEnd.coerceIn(0, textLength)
        }
    }

    // Calcola dove rimettere il cursore dopo una modifica automatica.
    private fun currentSelectionForRestore(textLength: Int): Pair<Int, Int> {
        val start = editText.selectionStart.takeIf { it >= 0 } ?: lastSelectionStart
        val end = editText.selectionEnd.takeIf { it >= 0 } ?: lastSelectionEnd
        return start.coerceIn(0, textLength) to end.coerceIn(0, textLength)
    }

    // Salva in memoria la posizione attuale del cursore.
    private fun rememberSelection() {
        val length = editText.text?.length ?: 0
        val start = editText.selectionStart
        val end = editText.selectionEnd
        if (start >= 0 && end >= 0) {
            lastSelectionStart = start.coerceIn(0, length) //Forza questo numero a stare tra 0 e la lunghezza massima del testo
            lastSelectionEnd = end.coerceIn(0, length)
        }
    }

    // Controlla se la tastiera sta ancora suggerendo/auto-correggendo una parola.
    private fun hasComposingText(text: CharSequence?): Boolean {
        if (text !is Spannable) return false
        val start = BaseInputConnection.getComposingSpanStart(text)
        val end = BaseInputConnection.getComposingSpanEnd(text)
        return start >= 0 && end > start
    }

    // Inserisce gli aggiornamenti in attesa appena l'utente finisce di digitare.
    private fun applyPendingExternalBindIfReady() {
        val pending = pendingExternalBind ?: return
        if (hasComposingText(editText.text)) return
        pendingExternalBind = null
        editText.post {
            if (!disposed) {
                setTextValue(pending.text, pending.entities)
            }
        }
    }

    private data class PendingExternalBind(
        val text: String,
        val entities: List<MessageEntity>,
    )

    private data class EmojiMetrics(
        val renderSizePx: Int,
        val fontMetrics: Paint.FontMetricsInt,
    )

    private data class DeleteRange(
        val start: Int,
        val end: Int,
    )

    // Carica il font corretto con il giusto spessore.
    private fun resolveTypeface(fontFamily: String?, fontWeight: Int): Typeface {
        return when {
            fontFamily.isNullOrBlank() || fontFamily.contains("Inter", ignoreCase = true) ->
                context.interTypeface(fontWeight)
            else -> Typeface.create(fontFamily, Typeface.NORMAL)
        }
    }

    private companion object {
        const val MAX_ANIMATED_EMOJI = 20
        const val COMPOSER_EMOJI_SCALE = 1.4f
    }
}
