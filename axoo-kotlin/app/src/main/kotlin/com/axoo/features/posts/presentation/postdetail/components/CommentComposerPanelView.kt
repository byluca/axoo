package com.axoo.features.posts.presentation.postdetail.components

import android.annotation.SuppressLint
import android.content.Context
import android.widget.LinearLayout
import com.axoo.core.anim.BottomSheetAnimator
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.features.posts.domain.Comment
import com.axoo.features.posts.presentation.composer.ComposerController
import com.axoo.features.posts.presentation.composer.ComposerEmojiPanelHostView
import com.axoo.ui.theme.NativeColors

@SuppressLint("ViewConstructor")
internal class CommentComposerPanelView(
    context: Context,
    private val composerController: ComposerController,
    onPayloadChanged: (String, List<MessageEntity>, Int, Int) -> Unit,
    onSelectionChanged: (Int, Int) -> Unit,
    onInputActivated: () -> Unit,
    onEmojiToggle: () -> Unit,
) : LinearLayout(context) {
    private var pickerPanelHeightPx: Int = 0
    private var pickerOpen: Boolean = false
    private var pickerAnimationRunning: Boolean = false
    private var playbackGate: EmojiPlaybackGate = EmojiPlaybackGate.Inactive
    private val pickerAnimator = BottomSheetAnimator()

    private val replyBar = CommentComposerInputBar(
        context = context,
        onInputActivated = onInputActivated,
        onEmojiToggle = onEmojiToggle,
        onPayloadChanged = onPayloadChanged,
        onSelectionChanged = onSelectionChanged,
    )

    private val pickerHost = ComposerEmojiPanelHostView(
        context = context,
        composerController = composerController,

    ).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0)
        visibility = GONE
    }

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        addView(replyBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(pickerHost)
        replyBar.bindComposer(composerController)
        replyBar.setEmojiPlaybackGate(playbackGate)
    }

    val inputBarHeight: Int get() = replyBar.measuredHeight

    fun setOnMediaRequested(callback: (Boolean) -> Unit) { pickerHost.onMediaRequested = callback }

    fun setCallbacks(
        onSend: () -> Unit,
        onCancelReply: () -> Unit,
    ) {
        replyBar.setCallbacks(onSend = onSend)
        replyBar.setOnCancelReply(onCancelReply)
    }

    fun bind(
        text: String,
        entities: List<MessageEntity>,
        pickerOpen: Boolean,
        editing: Boolean,
        saving: Boolean,
        replyNickname: String?,
        replyComment: Comment?,
    ) {
        replyBar.bindReply(replyNickname.takeUnless { editing }, replyComment, enabled = !saving)
        replyBar.bind(
            text = text,
            entities = entities,
            editing = editing,
            saving = saving,
        )
        setPickerOpen(
            open = pickerOpen,
            emojiRequested = composerController.pickerOpen,
        )
    }

    fun moveCursorToEnd() {
        replyBar.moveCursorToEnd()
    }

    fun moveCursorTo(offset: Int) = replyBar.moveCursorTo(offset)

    fun finishComposition() = replyBar.finishComposition()

    fun setEmojiInputActive(active: Boolean) {
        replyBar.setEmojiInputActive(active)
        if (!active) {
            pickerHost.setActive(false)
        }
    }

    fun setScreenPlaybackGate(gate: EmojiPlaybackGate) {
        playbackGate = gate
        replyBar.setEmojiPlaybackGate(gate)
        pickerHost.setEmojiPlaybackGate(gate)
    }

    fun setPickerPanelHeight(heightPx: Int) {
        val safeHeight = heightPx.coerceAtLeast(0)
        if (pickerPanelHeightPx == safeHeight) return
        pickerPanelHeightPx = safeHeight
        val params = pickerHost.layoutParams as LayoutParams
        if (pickerOpen) params.height = pickerPanelHeightPx
        pickerHost.layoutParams = params
        requestLayout()
    }

    fun setNavigationBottomInset(bottomInsetPx: Int) {
        pickerHost.setNavigationBottomInset(bottomInsetPx)
    }

    fun isPickerAnimationRunning(): Boolean = pickerAnimationRunning

    fun isPickerOpen(): Boolean = pickerOpen

    fun setPickerOpen(
        open: Boolean,
        animate: Boolean = true,
        emojiRequested: Boolean = open,
        collapsedHeightPx: Int = 0,
        onHidden: () -> Unit = {},
    ) {
        val changed = pickerOpen != open
        pickerOpen = open
        replyBar.setPickerOpen(emojiRequested)
        if (!changed) {
            if (!animate && pickerAnimationRunning) {
                if (open) showPickerImmediately() else hidePickerImmediately()
            }
            return
        }
        if (open) {
            pickerHost.ensurePicker()
            pickerHost.setEmojiPlaybackGate(playbackGate)
            val resolvedHeight = if (pickerPanelHeightPx > 0) pickerPanelHeightPx else defaultPickerHeightPx()
            val params = pickerHost.layoutParams as LayoutParams
            params.height = if (animate) {
                collapsedHeightPx.coerceIn(0, resolvedHeight)
            } else {
                resolvedHeight
            }
            pickerHost.layoutParams = params
            pickerHost.visibility = VISIBLE
            pickerHost.translationY = 0f
            pickerHost.alpha = 1f
            cancelPickerAnimation()
            if (!animate) {
                pickerHost.setActive(true)
            } else {
                pickerHost.setActive(false)
                pickerAnimationRunning = true
                pickerAnimator.showHeight(pickerHost, resolvedHeight) {
                    pickerAnimationRunning = false
                    if (pickerOpen) pickerHost.setActive(true)
                }
            }
        } else {
            pickerHost.setActive(false)
            cancelPickerAnimation()
            if (!animate) {
                hidePickerImmediately()
                return
            }
            val params = pickerHost.layoutParams as LayoutParams
            val currentHeight = params.height.takeIf { it > 0 }
                ?: pickerHost.height.takeIf { it > 0 }
                ?: pickerPanelHeightPx.coerceAtLeast(1)
            val collapsedHeight = collapsedHeightPx.coerceIn(0, currentHeight)
            pickerAnimationRunning = true
            pickerAnimator.hideHeight(pickerHost, currentHeight, collapsedHeight) {
                pickerAnimationRunning = false
                if (pickerOpen) return@hideHeight
                hidePickerImmediately()
                onHidden()
            }
        }
        requestLayout()
    }

    fun dismissPickerLifecycle() {
        pickerHost.setActive(false)
    }

    fun dispose() {
        cancelPickerAnimation()
        pickerHost.dispose()
        replyBar.dispose()
    }

    private fun showPickerImmediately() {
        cancelPickerAnimation()
        val resolvedHeight = if (pickerPanelHeightPx > 0) pickerPanelHeightPx else defaultPickerHeightPx()
        val params = pickerHost.layoutParams as LayoutParams
        params.height = resolvedHeight
        pickerHost.layoutParams = params
        pickerHost.visibility = VISIBLE
        pickerHost.translationY = 0f
        pickerHost.alpha = 1f
        pickerHost.setActive(true)
        requestLayout()
    }

    private fun hidePickerImmediately() {
        cancelPickerAnimation()
        val params = pickerHost.layoutParams as LayoutParams
        params.height = 0
        pickerHost.layoutParams = params
        pickerHost.visibility = GONE
        pickerHost.translationY = 0f
        pickerHost.alpha = 1f
        requestLayout()
    }

    private fun cancelPickerAnimation() {
        pickerAnimator.cancel()
        pickerAnimationRunning = false
    }

    private fun defaultPickerHeightPx(): Int {
        val density = resources.displayMetrics.density
        return (300f * density).toInt().coerceAtLeast(1)
    }
}
