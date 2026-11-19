package com.axoo.features.posts.presentation.posteditor.components

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.axoo.engine.emoji.catalog.EmojiData
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.engine.emoji.text.EmojiTextEditor
import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.features.posts.presentation.components.MIXED_TEXT_LINE_SPACING
import com.axoo.features.posts.presentation.composer.ComposerController
import com.axoo.features.posts.presentation.posteditor.CREATE_POST_COUNTER_THRESHOLD
import com.axoo.features.posts.presentation.posteditor.MAX_CREATE_POST_CHARACTERS
import com.axoo.features.posts.presentation.posteditor.postEditorCharacterCount
import com.axoo.features.posts.presentation.posteditor.PostEditorEvent
import com.axoo.features.posts.domain.PollOptionInput
import com.axoo.ui.components.NativeEmojiKeyboardToggleView
import com.axoo.ui.components.applyNativePillSurface
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp
import com.axoo.ui.theme.interTypeface

@SuppressLint("ViewConstructor")
internal class NativePostEditorBodyView(
    context: Context,
    textPrimaryArgb: Int,
    onPayloadChanged: (String, List<MessageEntity>) -> Unit,
    onEmojiToggle: () -> Unit,
    onInputActivated: () -> Unit,
) : FrameLayout(context) {

    private val editorScroll = ScrollView(context).apply {
        overScrollMode = View.OVER_SCROLL_ALWAYS
    }
    private val container = LinearLayout(context)
    private val header = NativePostEditorHeaderView(context)
    private val headerSpacer = View(context)
    private val toggle = NativeEmojiKeyboardToggleView(context)
    private val toggleSpacer = View(context)
    private val inputContainer = FrameLayout(context)
    private val emojiInput: EmojiTextEditor
    private val hintTextView = TextView(context)
    private val characterCounter = TextView(context)
    private val mediaPreview = NativeSelectedMediaPreviewView(context)
    private var activeInput: EmojiTextEditor? = null
    private val pollEditor = PostPollEditorView(context,
        activate = { input ->
            activeInput = input
            composerController?.bindInput(input)
            onInputActivated()
        },
        unbind = { input ->
            composerController?.unbindInput(input)
            if (activeInput === input) {
                activeInput = emojiInput
                composerController?.bindInput(emojiInput)
            }
        },
    )

    private var onFlairTapCb: () -> Unit = {}
    private var onRemoveMediaCb: () -> Unit = {}
                        private var onEmojiToggleCb: () -> Unit = onEmojiToggle
    private var currentText: String = ""
    private var composerController: ComposerController? = null
    private var disposed = false
    val hasMedia: Boolean
        get() = mediaPreview.visibility == View.VISIBLE

    init {
        clipToPadding = false
        applyNativePillSurface(cornerRadiusDp = PostEditorStyle.cardRadius)

        container.orientation = LinearLayout.VERTICAL
        val padH = context.dp(PostEditorStyle.contentInset)
        container.setPadding(padH, padH, padH, padH)
        editorScroll.isFillViewport = false
        editorScroll.clipToPadding = false
        editorScroll.addView(
            container,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(
            editorScroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(
            mediaPreview,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        container.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        container.addView(
            headerSpacer,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(PostEditorStyle.sectionSpacing)),
        )

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        row.addView(
            toggle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { 
                gravity = Gravity.TOP
                leftMargin = -context.dp(8)
            },
        )
        row.addView(
            toggleSpacer,
            LinearLayout.LayoutParams(context.dp(8), LinearLayout.LayoutParams.WRAP_CONTENT),
        )

        emojiInput = EmojiTextEditor(
            context = context,
            emojis = EmojiData.emojis,
            fontSizeSp = PostEditorStyle.bodyTextSize,
            fontWeight = 400,
            fontFamily = "Inter",
            hintText = "",
            textColor = textPrimaryArgb,
            cursorColor = NativeColors.accent,
            lineSpacingMultiplier = MIXED_TEXT_LINE_SPACING,
            maxLines = Int.MAX_VALUE,
            verticalPaddingPx = context.dp(PostEditorStyle.textVerticalInset),
            onTextChanged = { text, emojiEntities ->
                currentText = text
                updateWritingIndicators()
                onPayloadChanged(text, emojiEntities)
            },
        )
        emojiInput.getView().apply {
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    activeInput = emojiInput
                    composerController?.bindInput(emojiInput)
                    onInputActivated()
                }
            }
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    activeInput = emojiInput
                    composerController?.bindInput(emojiInput)
                    onInputActivated()
                }
                false
            }
        }
        inputContainer.addView(
            emojiInput.getView(),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        hintTextView.apply {
            text = PostEditorStyle.hint
            setTextColor(NativeColors.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, PostEditorStyle.hintTextSize)
            typeface = context.interTypeface(400)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.START or Gravity.TOP
            setPadding(0, context.dp(PostEditorStyle.textVerticalInset), 0, 0)
            isClickable = false
            isFocusable = false
        }
        inputContainer.addView(
            hintTextView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        inputContainer.minimumHeight = context.dp(DEFAULT_INPUT_MIN_HEIGHT_DP)
        row.addView(
            inputContainer,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.TOP
            },
        )
        container.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        characterCounter.apply {
            visibility = View.GONE
            gravity = Gravity.END
            textSize = PostEditorStyle.counterTextSize
            includeFontPadding = false
            typeface = context.interTypeface(600)
            setTextColor(NativeColors.textSecondary)
        }
        container.addView(
            characterCounter,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = context.dp(4) },
        )

        header.setOnFlairTap { onFlairTapCb() }
        container.addView(pollEditor, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = context.dp(8) })
        mediaPreview.setOnRemove { onRemoveMediaCb() }
                toggle.setOnClickListener {
            onEmojiToggleCb()
        }

        updateWritingIndicators()
    }

    fun setOnFlairTap(callback: () -> Unit) { onFlairTapCb = callback }
    fun setOnRemoveMedia(callback: () -> Unit) { onRemoveMediaCb = callback }
    fun setOnPollEvent(callback: (PostEditorEvent) -> Unit) { pollEditor.onEvent = callback }
                        fun setOnEmojiToggle(callback: () -> Unit) { onEmojiToggleCb = callback }

    fun bindComposer(controller: ComposerController) {
        if (composerController === controller) return
        composerController?.unbindInput(emojiInput)
        composerController = controller
        controller.bindInput(emojiInput)
        activeInput = emojiInput
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        composerController?.let { controller ->
            controller.unbindInput(emojiInput)
        }
        composerController = null
        emojiInput.dispose()
        pollEditor.dispose()
        mediaPreview.dispose()
    }

    fun setEmojiInputActive(active: Boolean) {
        emojiInput.setActive(active)
        pollEditor.setActive(active)
    }

    fun setEmojiPlaybackGate(gate: EmojiPlaybackGate) {
        emojiInput.setEmojiPlaybackGate(gate)
        pollEditor.setPlaybackGate(gate)
    }

    fun bind(
        seed: String,
        avatarConfig: Map<String, Any>?,
        nickname: String?,
        universityName: String?,
        selectedFlair: String?,
        bodyText: String,
        bodyEntities: List<MessageEntity>,
        mediaItem: MediaItem?,
                pickerOpen: Boolean,
        textSecondaryArgb: Int,
        moveCursorToEnd: Boolean,
        pollOptions: List<PollOptionInput>? = null,
        enabled: Boolean = true,
    ) {
        val campusName = universityName?.takeIf(String::isNotBlank)
        header.bind(seed, avatarConfig, nickname, selectedFlair, universityName)

        toggle.bind(
            pickerOpen = pickerOpen,
            tint = textSecondaryArgb,
            iconSizePx = context.dp(24f),
            hitSizePx = context.dp(40f),
        )

        if (bodyText != currentText) {
            currentText = bodyText
            emojiInput.setTextValue(
                bodyText,
                bodyEntities,
            )
            if (moveCursorToEnd) emojiInput.moveCursorToEnd()
        }
        updateWritingIndicators()
        inputContainer.minimumHeight = context.dp(
            if (pollOptions != null) POLL_INPUT_MIN_HEIGHT_DP else DEFAULT_INPUT_MIN_HEIGHT_DP,
        )
        mediaPreview.bind(mediaItem)
        emojiInput.getView().isEnabled = enabled
        pollEditor.bind(pollOptions, enabled)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val previewHorizontalMargin = context.dp(PostEditorStyle.contentInset)
        val previewTopMargin = context.dp(PostEditorStyle.sectionSpacing)
        val previewBottomMargin = context.dp(PostEditorStyle.contentInset)
        val previewViewport = if (hasMedia) {
            val previewBudget = (
                height -
                    context.dp(MIN_EDITOR_AREA_DP) -
                    previewTopMargin -
                    previewBottomMargin
                ).coerceAtLeast(context.dp(MIN_COMPACT_PREVIEW_DP))
            val maxPreviewSize = minOf(
                context.dp(MEDIA_PREVIEW_MAX_SIZE_DP),
                previewBudget,
                (width - previewHorizontalMargin * 2).coerceAtLeast(0),
            )
            mediaPreview.viewport(maxPreviewSize)
        } else {
            MediaPreviewViewport(0, 0)
        }

        mediaPreview.measure(
            MeasureSpec.makeMeasureSpec(previewViewport.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(previewViewport.height, MeasureSpec.EXACTLY),
        )
        val reservedPreviewHeight = if (hasMedia) {
            previewViewport.height + previewTopMargin + previewBottomMargin
        } else {
            0
        }
        val editorHeightLimit = (height - reservedPreviewHeight).coerceAtLeast(0)
        editorScroll.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(editorHeightLimit, MeasureSpec.AT_MOST),
        )
        val desiredHeight = editorScroll.measuredHeight + reservedPreviewHeight
        val finalHeight = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            height
        } else {
            minOf(height, desiredHeight)
        }
        setMeasuredDimension(width, finalHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        editorScroll.layout(0, 0, width, editorScroll.measuredHeight)

        val previewLeft = ((width - mediaPreview.measuredWidth) / 2).coerceAtLeast(0)
        val previewTop = height - context.dp(PostEditorStyle.contentInset) - mediaPreview.measuredHeight
        mediaPreview.layout(
            previewLeft,
            previewTop,
            previewLeft + mediaPreview.measuredWidth,
            previewTop + mediaPreview.measuredHeight,
        )
    }

    private fun updateWritingIndicators() {
        hintTextView.visibility = if (currentText.isEmpty()) View.VISIBLE else View.GONE
        val count = postEditorCharacterCount(currentText)
        characterCounter.visibility = if (count >= CREATE_POST_COUNTER_THRESHOLD) {
            View.VISIBLE
        } else {
            View.GONE
        }
        characterCounter.text = "$count/$MAX_CREATE_POST_CHARACTERS"
        characterCounter.setTextColor(
            if (count > MAX_CREATE_POST_CHARACTERS) NativeColors.destructive else NativeColors.textSecondary,
        )
    }

    private companion object {
        const val MEDIA_PREVIEW_MAX_SIZE_DP = PostEditorStyle.maximumMediaSize
        const val MIN_EDITOR_AREA_DP = 180
        const val MIN_COMPACT_PREVIEW_DP = 88
        const val DEFAULT_INPUT_MIN_HEIGHT_DP = PostEditorStyle.minimumTextHeight
        const val POLL_INPUT_MIN_HEIGHT_DP = DEFAULT_INPUT_MIN_HEIGHT_DP - 48
            }
}
