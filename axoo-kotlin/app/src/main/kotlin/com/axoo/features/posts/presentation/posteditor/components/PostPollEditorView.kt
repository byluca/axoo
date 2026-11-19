package com.axoo.features.posts.presentation.posteditor.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import com.axoo.engine.emoji.catalog.EmojiData
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.engine.emoji.text.EmojiTextEditor
import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.features.posts.domain.MAX_POLL_OPTIONS
import com.axoo.features.posts.domain.MAX_POLL_OPTION_CHARACTERS
import com.axoo.features.posts.domain.MIN_POLL_OPTIONS
import com.axoo.features.posts.domain.PollOptionInput
import com.axoo.features.posts.presentation.posteditor.PostEditorEvent
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp
import com.axoo.ui.theme.interTypeface

@SuppressLint("ViewConstructor", "SetTextI18n")
internal class PostPollEditorView(
    context: Context,
    private val activate: (EmojiTextEditor) -> Unit,
    private val unbind: (EmojiTextEditor) -> Unit,
) : LinearLayout(context) {
    var onEvent: (PostEditorEvent) -> Unit = {}
    private val rows = mutableListOf<OptionRow>()
    private var active = true
    private var gate = EmojiPlaybackGate.Inactive
    private val addButton = TextView(context).apply {
        text = "+ Aggiungi opzione"
        textSize = 14f
        typeface = context.interTypeface(500)
        setTextColor(NativeColors.accent)
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(44)
        setOnClickListener { onEvent(PostEditorEvent.PollOptionAdded) }
    }
    private val note = TextView(context).apply {
        text = "Una sola scelta. Dopo la pubblicazione il post non è modificabile."
        textSize = 12f
        typeface = context.interTypeface(400)
        setTextColor(NativeColors.textSecondary)
        setPadding(0, dp(8), 0, dp(4))
    }

    init { orientation = VERTICAL; visibility = GONE }

    fun bind(options: List<PollOptionInput>?, enabled: Boolean) {
        visibility = if (options == null) GONE else VISIBLE
        val count = options?.size ?: 0
        while (rows.size > count) {
            val row = rows.removeAt(rows.lastIndex)
            unbind(row.editor)
            row.editor.dispose()
            removeView(row)
        }
        while (rows.size < count) {
            val row = OptionRow(rows.size)
            rows.add(row)
            addView(row, rows.lastIndex, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        if (addButton.parent == null) {
            addView(addButton, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(note, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        rows.forEachIndexed { index, row -> row.bind(options!![index], count > MIN_POLL_OPTIONS, enabled) }
        addButton.visibility = if (count < MAX_POLL_OPTIONS) VISIBLE else GONE
        addButton.isEnabled = enabled
    }

    fun setActive(value: Boolean) { active = value; rows.forEach { it.editor.setActive(value) } }
    fun setPlaybackGate(value: EmojiPlaybackGate) { gate = value; rows.forEach { it.editor.setEmojiPlaybackGate(value) } }
    fun dispose() { rows.forEach { unbind(it.editor); it.editor.dispose() }; rows.clear() }

    private inner class OptionRow(private val index: Int) : LinearLayout(context) {
        private var payload = PollOptionInput()
        val editor = EmojiTextEditor(
            context, EmojiData.emojis,
            fontSizeSp = 16f, fontFamily = "Inter", hintText = "Opzione ${index + 1}",
            maxLines = 4, verticalPaddingPx = dp(12),
            onTextChanged = { text, entities ->
                payload = PollOptionInput(text, entities)
                updateCounter()
                onEvent(PostEditorEvent.PollOptionChanged(index, text, entities))
            },
        )
        private val counter = TextView(context).apply {
            textSize = 11f
            typeface = context.interTypeface(400)
            includeFontPadding = false
        }
        private val remove = ImageView(context).apply {
            contentDescription = "Rimuovi opzione ${index + 1}"
            load("file:///android_asset/icons/close-square.svg")
            imageTintList = android.content.res.ColorStateList.valueOf(NativeColors.textSecondary)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { onEvent(PostEditorEvent.PollOptionRemoved(index)) }
        }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            val fields = LinearLayout(context).apply {
                orientation = VERTICAL
                addView(editor.getView(), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                addView(counter, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            }
            addView(View(context).apply { background = GradientDrawable().apply {
                cornerRadius = dp(4).toFloat()
                setColor(android.graphics.Color.TRANSPARENT)
                setStroke(dp(1), NativeColors.cardBorder)
            } }, LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(12) })
            addView(fields, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(remove, LayoutParams(dp(44), dp(44)))
            editor.getView().apply {
                contentDescription = "Opzione ${index + 1}"
                setOnFocusChangeListener { _, focused -> if (focused) activate(editor) }
                setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) activate(editor)
                    false
                }
            }
            editor.setActive(active)
            editor.setEmojiPlaybackGate(gate)
            // A single hairline separates adjacent options.
            background = object : android.graphics.drawable.Drawable() {
                private val paint = android.graphics.Paint().apply { color = NativeColors.divider }
                override fun draw(canvas: android.graphics.Canvas) {
                    canvas.drawRect(0f, bounds.bottom - dp(1).toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), paint)
                }
                override fun setAlpha(alpha: Int) { paint.alpha = alpha }
                override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
                @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
            }
        }

        fun bind(value: PollOptionInput, canRemove: Boolean, enabled: Boolean) {
            if (payload != value) { payload = value; editor.setTextValue(value.text, value.entities) }
            editor.getView().isEnabled = enabled
            remove.visibility = if (canRemove) VISIBLE else GONE
            remove.isEnabled = enabled
            updateCounter()
        }

        private fun updateCounter() {
            val count = payload.text.codePointCount(0, payload.text.length)
            counter.visibility = if (count >= 80) VISIBLE else GONE
            counter.text = "$count/$MAX_POLL_OPTION_CHARACTERS"
            counter.setTextColor(if (count > MAX_POLL_OPTION_CHARACTERS) NativeColors.accent else NativeColors.textSecondary)
        }
    }
}
