package com.axoo.features.posts.presentation.composer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.widget.FrameLayout
import com.axoo.engine.emoji.catalog.EmojiData
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.engine.emoji.picker.EmojiPanelView

/** Shared owner for the emoji picker used by post and comment composers. */
@SuppressLint("ViewConstructor")
internal class ComposerEmojiPanelHostView(
    context: Context,
    private val composerController: ComposerController,
    private val supportsMedia: Boolean = true,
) : FrameLayout(context) {

    var onMediaRequested: (Boolean) -> Unit = {}
    private var pickerView: EmojiPanelView? = null
    private var navigationBottomInsetPx = 0
    private var playbackGate: EmojiPlaybackGate = EmojiPlaybackGate.Inactive

    val isPickerInitialized: Boolean
        get() = pickerView != null

    init {
        setBackgroundColor(Color.TRANSPARENT)
        visibility = GONE
        clipChildren = false
        clipToPadding = false
    }

    fun ensurePicker() {
        if (pickerView != null) return
        val picker = EmojiPanelView(
            context = context,
            sections = EmojiData.categories,
            hasBackspace = true,
            bottomPaddingDp = 0,
            backgroundColor = com.axoo.ui.theme.NativeColors.pickerBackground,
            delegate = composerController,
            onMediaRequested = if (supportsMedia) ({ onMediaRequested(it) }) else null,
        )
        picker.setEmojiPlaybackGate(playbackGate)
        picker.setNavigationBottomInset(navigationBottomInsetPx)
        pickerView = picker
        removeAllViews()
        addView(
            picker.getView(),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    fun setActive(active: Boolean) {
        if (active) ensurePicker()
        pickerView?.setActive(active)
    }

    fun setEmojiPlaybackGate(gate: EmojiPlaybackGate) {
        if (playbackGate == gate) return
        playbackGate = gate
        pickerView?.setEmojiPlaybackGate(gate)
    }

    fun setNavigationBottomInset(bottomInsetPx: Int) {
        navigationBottomInsetPx = bottomInsetPx.coerceAtLeast(0)
        pickerView?.setNavigationBottomInset(navigationBottomInsetPx)
    }

    fun dispose() {
        pickerView?.dispose()
        pickerView = null
        removeAllViews()
    }

}
