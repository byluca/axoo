package com.axoo.features.posts.presentation.postdetail.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.Spannable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import coil.load
import com.axoo.engine.emoji.catalog.EmojiData
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.engine.emoji.text.EmojiTextEditor
import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.features.posts.domain.Comment
import com.axoo.features.posts.presentation.composer.ComposerController
import com.axoo.ui.components.NativeEmojiKeyboardToggleView
import com.axoo.ui.components.NativePillDefaults
import com.axoo.ui.components.buttonActivityIndicator
import com.axoo.ui.components.setControlShadow
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp
import com.axoo.ui.theme.interTypeface

/**
 * Barra di input per i commenti (Post Detail).
 * Struttura: Una "pillola" espandibile (fino a ~4 righe) con toggle emoji a sinistra,
 * e un tasto di invio circolare a destra.
 */
@SuppressLint("ViewConstructor")
internal class CommentComposerInputBar(
    context: Context,
    private val onInputActivated: () -> Unit,
    private val onEmojiToggle: () -> Unit,
    onPayloadChanged: (String, List<MessageEntity>, Int, Int) -> Unit,
    onSelectionChanged: (Int, Int) -> Unit,
) : FrameLayout(context) {

    // --- COMPONENTI UI ---
    private val row = LinearLayout(context) // Contenitore orizzontale principale
    private val composerPill = LinearLayout(context) // La "pillola" con bordi arrotondati
    private val replyPreview = CommentReplyPreviewView(context)
    private val composerColumn = LinearLayout(context)
    private val toggle = NativeEmojiKeyboardToggleView(context) // Tasto per aprire le emoji
    private val inputField = FrameLayout(context) // Contenitore per il testo e l'hint
    private val emojiInput: EmojiTextEditor // Il motore di testo vero e proprio
    private val hintTextView = TextView(context) // Placeholder testuale ("Commenta...")
    private val sendButton = FrameLayout(context) // Tasto di invio a destra
    private val sendIcon = ImageView(context)
    private val sendActivity = buttonActivityIndicator(context)

    // --- STATO ---
    private var pickerOpen = false
    private var currentText: String = ""
    private var sendEnabled = false
    private var editing = false
    private var saving = false
    private var renderedSaving = false
    private var onSend: () -> Unit = {}
    private var composerController: ComposerController? = null
    private var disposed = false

    init {
        clipChildren = false
        clipToPadding = false
        setPadding(0, 0, 0, 0)

        // Setup riga principale (Allinea gli elementi in basso)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.BOTTOM
        row.clipChildren = false
        row.clipToPadding = false

        // Setup pillola curva
        composerPill.orientation = LinearLayout.HORIZONTAL
        composerPill.gravity = Gravity.CENTER_VERTICAL
        composerPill.minimumHeight = context.dp(44)
        composerPill.background = GradientDrawable().apply {
            setColor(NativeColors.card)
            cornerRadius = context.dp(NativePillDefaults.controlCornerRadiusDp.toFloat())
            setStroke(context.dp(NativePillDefaults.borderWidthDp).toInt().coerceAtLeast(1),
                NativePillDefaults.borderColor)
        }
        composerPill.setPadding(context.dp(4), context.dp(2), context.dp(8), context.dp(2))
        composerPill.setControlShadow(context.dp(NativePillDefaults.controlCornerRadiusDp.toFloat()))

        // Tasto emoji toggle con listener (quindi utente se clicca si apre emojipicker)
        toggle.setOnClickListener { onEmojiToggle() }
        composerPill.addView(
            toggle,
            LinearLayout.LayoutParams(context.dp(40), context.dp(40)).apply {
                gravity = Gravity.BOTTOM
            },
        )

        // Area di testo
        inputField.background = null
        inputField.setPadding(context.dp(12), 0, context.dp(8), 0)

        // Inizializzazione Editor (Cresce all'infinito per evitare bug di scroll interno)
        emojiInput = EmojiTextEditor(
            context = context,
            emojis = EmojiData.emojis,
            fontSizeSp = 16f,
            fontWeight = 400,
            fontFamily = "Inter",
            hintText = "",
            textColor = NativeColors.textPrimary,
            cursorColor = NativeColors.accent,
            editGravity = Gravity.START or Gravity.TOP,
            verticalPaddingPx = context.dp(10),
            maxLines = Int.MAX_VALUE,
            lineSpacingMultiplier = 1.12f,
            autoBringPointIntoView = true,
            onSelectionChanged = onSelectionChanged,
            onTextChanged = { t, emojiEntities ->
                currentText = t
                updateWritingIndicators()
                val editor = editorTextView()
                onPayloadChanged(t, emojiEntities, editor.selectionStart, editor.selectionEnd)
            },
        )

        // Gestione del focus sulla tastiera
        emojiInput.getView().apply {
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) onInputActivated()
            }
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onInputActivated()
                }
                false
            }
        }

        // ScrollView custom: Fa scorrere il testo solo dopo le ~4 righe (88dp).
        // Previene i fastidiosi salti grafici delegando lo scroll al contenitore padre.
        val editorScroll = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val maxHeight = context.dp(120) // Limite di ~6 righe
                val heightSize = MeasureSpec.getSize(heightMeasureSpec)
                val heightMode = MeasureSpec.getMode(heightMeasureSpec)
                val newHeightSpec = if (heightMode == MeasureSpec.UNSPECIFIED || heightSize > maxHeight) {
                    MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
                } else {
                    heightMeasureSpec
                }
                super.onMeasure(widthMeasureSpec, newHeightSpec)
            }
        }.apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
        }

        editorScroll.addView(
            emojiInput.getView(),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ),
        )
        inputField.addView(
            editorScroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ),
        )

        // Hint testuale custom (Sovrapposto al testo, gestito manualmente per allineamento perfetto)
        hintTextView.apply {
            text = "Commenta…"
            setTextColor(NativeColors.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = context.interTypeface(400)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.START or Gravity.TOP
            setPadding(0, context.dp(10), 0, 0)
            isClickable = false
            isFocusable = false
        }
        inputField.addView(
            hintTextView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.TOP,
            ),
        )

        composerPill.addView(
            inputField,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            },
        )

        // Tasto Invia
        sendButton.addView(sendIcon, FrameLayout.LayoutParams(context.dp(25), context.dp(25), Gravity.CENTER))
        sendButton.addView(sendActivity, FrameLayout.LayoutParams(context.dp(18), context.dp(18), Gravity.CENTER))
        sendIcon.scaleType = ImageView.ScaleType.FIT_CENTER
        sendIcon.load(assetIcon("send"))
        sendIcon.setColorFilter(NativeColors.onAccent, PorterDuff.Mode.SRC_IN)
        sendButton.isClickable = true
        sendButton.isFocusable = true
        sendButton.setOnClickListener {
            if (!sendEnabled) return@setOnClickListener
            onSend()
        }

        // Assemblaggio finale
        composerColumn.orientation = LinearLayout.VERTICAL
        composerColumn.clipChildren = false
        composerColumn.clipToPadding = false
        composerColumn.setPadding(context.dp(8), context.dp(5), context.dp(8), context.dp(KEYBOARD_GAP_DP))
        composerColumn.addView(replyPreview,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(
            composerPill,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.BOTTOM
                marginEnd = context.dp(8)
            },
        )
        row.addView(
            sendButton,
            LinearLayout.LayoutParams(context.dp(44), context.dp(44)).apply {
                gravity = Gravity.BOTTOM
            },
        )

        composerColumn.addView(row,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(composerColumn, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        updateWritingIndicators()
        bindToggle()
    }

    fun setCallbacks(onSend: () -> Unit) {
        this.onSend = onSend
    }

    fun setOnCancelReply(callback: () -> Unit) = replyPreview.setOnCancel(callback)

    fun bindReply(nickname: String?, comment: Comment?, enabled: Boolean) = replyPreview.bind(nickname, comment, enabled)

    fun bindComposer(controller: ComposerController) {
        if (composerController === controller) return
        composerController?.unbindInput(emojiInput)
        composerController = controller
        controller.bindInput(emojiInput)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        replyPreview.dispose()
        composerController?.let { controller ->
            controller.unbindInput(emojiInput)
        }
        composerController = null
        emojiInput.dispose()
    }

    fun setEmojiInputActive(active: Boolean) {
        emojiInput.setActive(active)
        replyPreview.setActive(active)
    }

    fun setEmojiPlaybackGate(gate: EmojiPlaybackGate) {
        emojiInput.setEmojiPlaybackGate(gate)
        replyPreview.setEmojiPlaybackGate(gate)
    }

    /**
     * Aggiorna lo stato visivo dall'esterno (es. modifica testo in corso, salvataggio).
     */
    fun bind(
        text: String,
        entities: List<MessageEntity>,
        editing: Boolean,
        saving: Boolean,
    ) {
        if (text != currentText) {
            currentText = text
            emojiInput.setTextValue(text, entities)
            updateWritingIndicators()
        }

        // Cambia icona se siamo in modalità modifica anziché inserimento
        if (this.editing != editing) {
            this.editing = editing
            sendIcon.load(assetIcon(if (editing) "checkmark" else "send"))
        }

        // Blocca l'input durante il salvataggio in rete
        this.saving = saving
        sendIcon.visibility = if (saving) View.INVISIBLE else View.VISIBLE
        sendActivity.visibility = if (saving) View.VISIBLE else View.GONE
        emojiInput.getView().isEnabled = !saving
        toggle.isEnabled = !saving
        updateWritingIndicators()
    }

    fun moveCursorToEnd() {
        emojiInput.moveCursorToEnd()
    }

    fun moveCursorTo(offset: Int) = emojiInput.moveCursorTo(offset)

    fun finishComposition() = emojiInput.finishComposition()

    private fun editorTextView(): TextView = emojiInput.getView() as TextView

    fun setPickerOpen(open: Boolean) {
        if (pickerOpen == open) return
        pickerOpen = open
        bindToggle()
    }

    /**
     * Gestisce la comparsa dell'hint testuale e l'accensione/spegnimento del tasto Invia.
     */
    private fun updateWritingIndicators() {
        // Mostra/Nascondi placeholder
        hintTextView.text = if (editing) "Modifica il commento…" else "Commenta…"
        hintTextView.visibility = if (currentText.isEmpty()) View.VISIBLE else View.GONE

        // Abilita tasto Invia solo se c'è testo valido (e max 500 rune) e non stiamo già salvando
        val runeCount = currentText.codePointCount(0, currentText.length)
        val nextEnabled = !saving && currentText.isNotBlank() && runeCount <= 500
        if (nextEnabled == sendEnabled && saving == renderedSaving && sendButton.background != null) {
            return
        }

        sendEnabled = nextEnabled
        renderedSaving = saving
        sendButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(context.dp(NativePillDefaults.borderWidthDp).toInt().coerceAtLeast(1), NativePillDefaults.borderColor)
            setColor(if (sendEnabled || saving) NativeColors.accent else ColorUtils.compositeColors(applyAlpha(NativeColors.accent, 0.28f), NativeColors.card))
        }
        sendButton.isEnabled = sendEnabled
        sendButton.setControlShadow(context.dp(NativePillDefaults.controlCornerRadiusDp.toFloat()))
        sendButton.alpha = 1f
    }

    private fun bindToggle() {
        toggle.bind(
            pickerOpen = pickerOpen,
            tint = NativeColors.textSecondary,
            iconSizePx = context.dp(28f),
            hitSizePx = context.dp(40f),
        )
    }

    private fun assetIcon(name: String) = "file:///android_asset/icons/$name.svg"

    private fun applyAlpha(argb: Int, alpha: Float): Int {
        val a = (((argb ushr 24) and 0xFF) * alpha).toInt().coerceIn(0, 255)
        return (a shl 24) or (argb and 0x00FFFFFF)
    }

    private companion object {
        const val KEYBOARD_GAP_DP = 12
    }
}
