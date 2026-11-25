/*
 * Costruisce la parte visiva della schermata Crea post.
 *
 * Posiziona top bar, editor, toolbar ed emoji picker e mostra lo stato ricevuto
 * dal PostEditorHost. GIF e Meme usano la sheet Material; le emoji restano nel composer.
 */
package com.axoo.features.posts.presentation.posteditor.components

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.features.posts.presentation.composer.BottomInputPanelController
import com.axoo.features.posts.domain.RemoteMediaType
import com.axoo.features.posts.presentation.composer.ComposerController
import com.axoo.features.posts.presentation.composer.ComposerEmojiPanelHostView
import com.axoo.features.posts.presentation.composer.ComposerPanelState
import com.axoo.features.posts.presentation.media.RemoteMediaState
import com.axoo.features.posts.presentation.media.components.PickerPanelContainer
import com.axoo.features.posts.presentation.media.components.RemoteMediaPickerSheet
import com.axoo.features.posts.presentation.posteditor.PostEditorEvent
import com.axoo.features.posts.presentation.posteditor.PostEditorHost
import com.axoo.features.posts.presentation.posteditor.PostEditorUiState
import com.axoo.features.posts.presentation.posteditor.PostMediaKind
import com.axoo.ui.components.defaultPickerPanelHeight
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp

internal fun shouldKeepEditorEmojiSlot(
    emojiRequested: Boolean,
    composerPanel: ComposerPanelState,
    panelShouldShowPicker: Boolean,
): Boolean = emojiRequested ||
    (composerPanel == ComposerPanelState.Keyboard && panelShouldShowPicker)

internal fun postEditorAvailableBodyHeight(
    totalHeight: Int,
    topBarHeight: Int,
    bodyTopMargin: Int,
    toolbarHeight: Int,
    pickerHeight: Int,
    bottomOffset: Int,
): Int = (totalHeight - topBarHeight - bodyTopMargin - toolbarHeight - pickerHeight - bottomOffset)
    .coerceAtLeast(0)

@SuppressLint("ViewConstructor")
internal class NativePostEditorView(
    context: Context,
    private val composerController: ComposerController,
    onPayloadChanged: (String, List<MessageEntity>) -> Unit,
) : FrameLayout(context) {
    private var eventSink: (PostEditorEvent) -> Unit = {}
    private var emojiRequested = false
    private var bottomSlot = BottomSlot.Closed
    private var remotePickerMode: RemoteMediaType? = null
    private var remotePickerSheet: RemoteMediaPickerSheet? = null
    private var bottomOffsetPx = 0
    private var navigationBottomInsetPx = 0
    private var playbackGate: EmojiPlaybackGate = EmojiPlaybackGate.Inactive
    private var playbackGateApplied = false
    private var emojiPlaybackActive = false
    private val panel = composerController.panelController
    private val contentHorizontalMargin = context.dp(PostEditorStyle.outerInset)
    private val bodyTopMargin = context.dp(PostEditorStyle.topInset)
    private val topBar = NativePostEditorTopBarView(context)
    private val body = NativePostEditorBodyView(
        context = context,
        textPrimaryArgb = NativeColors.textPrimary,
        onPayloadChanged = onPayloadChanged,
        onEmojiToggle = { eventSink(PostEditorEvent.EmojiToggleTapped) },
        onInputActivated = composerController::requestKeyboard,
    )
    private val toolbar = NativePostEditorToolbarView(context)
    private val pickerHost = ComposerEmojiPanelHostView(
        context = context,
        composerController = composerController,

    )
    private val pickerPanel = PickerPanelContainer(context).apply {
        addView(pickerHost, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    init {
        setBackgroundColor(NativeColors.surface)
        clipChildren = false
        clipToPadding = false

        topBar.setCallbacks(
            onBack = { eventSink(PostEditorEvent.BackTapped) },
            onPublish = { eventSink(PostEditorEvent.PublishTapped) },
        )
        body.setOnFlairTap { eventSink(PostEditorEvent.FlairTapped) }
        body.setOnRemoveMedia { eventSink(PostEditorEvent.MediaRemoved) }
        body.setOnPollEvent { eventSink(it) }
                                                body.setOnEmojiToggle { eventSink(PostEditorEvent.EmojiToggleTapped) }
        body.bindComposer(composerController)
        pickerHost.onMediaRequested = { sticker -> eventSink(if (sticker) PostEditorEvent.StickerTapped else PostEditorEvent.GifTapped) }
        toolbar.setCallbacks(
            onGifTap = { eventSink(PostEditorEvent.GifTapped) },
            onStickerTap = { eventSink(PostEditorEvent.StickerTapped) },
            onMemeTap = { eventSink(PostEditorEvent.MemeTapped) },
            onPhotoTap = { eventSink(PostEditorEvent.PhotoTapped) },
            onPollTap = { eventSink(PostEditorEvent.PollTapped) },
                    )

        addView(topBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(toolbar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(pickerPanel, LayoutParams(LayoutParams.MATCH_PARENT, defaultPickerHeight()))

        panel.listener = object : BottomInputPanelController.Listener {
            override fun onPanelStateChanged(
                targetBottom: Int,
                mode: BottomInputPanelController.Mode,
            ) {
                bottomOffsetPx = if (mode == BottomInputPanelController.Mode.Keyboard) targetBottom else 0
                finishEmojiToKeyboardHandoff(mode)
                pickerPanel.updateExpandedHeight(panel.pickerPanelHeight(defaultPickerHeight()))
                updateToolbarNavigationPadding()
                requestLayout()
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            navigationBottomInsetPx = nav
            pickerHost.setNavigationBottomInset(nav)
            panel.updateInsets(
                nav = nav,
                ime = ime,
                imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime()),
                widthPx = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels,
                orientation = resources.configuration.orientation,
            )
            composerController.reconcileImeVisibility(
                insets.isVisible(WindowInsetsCompat.Type.ime()),
            )
            insets
        }
        ViewCompat.setWindowInsetsAnimationCallback(
            this,
            object : WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE,
            ) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        panel.onImeAnimationStarted()
                    }
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>,
                ): WindowInsetsCompat = insets

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        panel.onImeAnimationEnd()
                    }
                }
            },
        )
        ViewCompat.requestApplyInsets(this)
    }

    fun setEventSink(callback: (PostEditorEvent) -> Unit) {
        eventSink = callback
    }

    fun setActiveMediaPicker(mode: RemoteMediaType?) {
        toolbar.setActivePicker(mode)
    }

    fun showRemotePicker(state: RemoteMediaState) {
        val mode = state.type
        if (remotePickerMode == mode && remotePickerSheet != null) {
            remotePickerSheet?.bind(state)
            return
        }
        hideRemotePicker()
        lateinit var sheet: RemoteMediaPickerSheet
        sheet = RemoteMediaPickerSheet(
            context = context,
            onMediaSelected = { eventSink(PostEditorEvent.RemoteMediaTapped(it)) },
            onSearchQueryChanged = { eventSink(PostEditorEvent.RemoteMediaSearchChanged(it)) },
            onLoadMore = { eventSink(PostEditorEvent.RemoteMediaLoadMore) },
            onRetry = { eventSink(PostEditorEvent.RemoteMediaRetry) },
            onDismissed = {
                if (remotePickerSheet === sheet) {
                    remotePickerSheet = null
                    remotePickerMode = null
                    eventSink(PostEditorEvent.RemoteMediaPickerDismissed)
                }
            },
        )
        remotePickerMode = mode
        remotePickerSheet = sheet
        sheet.bind(state)
        sheet.presentOn(this)
    }

    fun bindRemotePicker(state: RemoteMediaState) {
        remotePickerSheet?.bind(state)
    }

    fun hideRemotePicker() {
        val sheet = remotePickerSheet
        remotePickerSheet = null
        remotePickerMode = null
        sheet?.dismissImmediately()
    }

    fun render(
        uiState: PostEditorUiState,
        seed: String,
        avatarConfig: Map<String, Any>?,
        nickname: String?,
        universityName: String?,
        mediaItem: MediaItem?,
    ) {
        setBackgroundColor(NativeColors.surface)
        topBar.bind(
            canPublish = uiState.canPublish,
            isPublishing = uiState.isPublishing,
            isEditing = uiState.isEditing,
        )
        body.bind(
            seed = seed,
            avatarConfig = avatarConfig,
            nickname = nickname,
            universityName = universityName,
            selectedFlair = uiState.draft.selectedFlair,
            bodyText = uiState.draft.bodyText,
            bodyEntities = uiState.draft.bodyEntities,
            mediaItem = mediaItem,
                        pickerOpen = uiState.pickerOpen,
            textSecondaryArgb = NativeColors.textSecondary,
            moveCursorToEnd = uiState.isEditing,
            pollOptions = uiState.draft.pollOptions,
            enabled = uiState.isEditorReady && !uiState.isPublishing,
        )
        toolbar.bind(
            hasGif = uiState.hasGif,
            hasSticker = uiState.draft.selectedMedia?.kind == PostMediaKind.Sticker,
            hasMeme = uiState.hasMeme,
            hasPhoto = uiState.draft.selectedMedia?.kind == PostMediaKind.Photo,
            hasPoll = uiState.draft.pollOptions != null,
            pollEnabled = !uiState.isEditing && uiState.isEditorReady && !uiState.isPublishing,
                    )

        setPickerOpen(uiState.pickerOpen)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val exactWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val bodyWidth = (width - contentHorizontalMargin * 2).coerceAtLeast(0)
        val exactBodyWidth = MeasureSpec.makeMeasureSpec(bodyWidth, MeasureSpec.EXACTLY)
        val atMostHeight = MeasureSpec.makeMeasureSpec(height.coerceAtLeast(0), MeasureSpec.AT_MOST)

        topBar.measure(exactWidth, atMostHeight)
        toolbar.measure(exactWidth, atMostHeight)

        val panelVisible = pickerPanel.isVisible
        val bottomOffset = if (panelVisible) 0 else bottomOffsetPx
        val pickerHeight = if (panelVisible) {
            pickerPanel.layoutParams.height.coerceAtLeast(0)
        } else {
            0
        }
        pickerPanel.measure(
            exactWidth,
            MeasureSpec.makeMeasureSpec(pickerHeight, MeasureSpec.EXACTLY),
        )
        val availableContentHeight = postEditorAvailableBodyHeight(
            totalHeight = height,
            topBarHeight = topBar.measuredHeight,
            bodyTopMargin = bodyTopMargin,
            toolbarHeight = toolbar.measuredHeight,
            pickerHeight = pickerHeight,
            bottomOffset = bottomOffset,
        )
        body.measure(
            exactBodyWidth,
            MeasureSpec.makeMeasureSpec(availableContentHeight, MeasureSpec.AT_MOST),
        )

        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        val topHeight = topBar.measuredHeight
        val panelVisible = pickerPanel.isVisible
        val pickerHeight = if (panelVisible) pickerPanel.measuredHeight else 0
        val bottomOffset = if (panelVisible) 0 else bottomOffsetPx
        val toolbarHeight = toolbar.measuredHeight
        val bottomBarTop = height - toolbarHeight - pickerHeight - bottomOffset
        val pickerTop = height - pickerHeight - bottomOffset
        val pickerBottom = height - bottomOffset
        val bodyTop = (topHeight + bodyTopMargin).coerceAtMost(bottomBarTop)
        val bodyBottom = (bodyTop + body.measuredHeight).coerceAtMost(bottomBarTop)

        topBar.layout(0, 0, width, topHeight)
        body.layout(
            contentHorizontalMargin,
            bodyTop,
            (width - contentHorizontalMargin).coerceAtLeast(contentHorizontalMargin),
            bodyBottom.coerceAtLeast(bodyTop),
        )
        toolbar.layout(0, bottomBarTop, width, pickerTop)
        pickerPanel.layout(0, pickerTop, width, pickerBottom)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    fun onHostStarted() {
        body.setEmojiInputActive(true)
    }

    fun onHostStopped() {
        composerController.dismissAll()
        pickerHost.setActive(false)
        emojiPlaybackActive = false
        hideRemotePicker()
        pickerPanel.hideImmediately()
        emojiRequested = false
        bottomSlot = BottomSlot.Closed
        panel.resetImeAnimationState()
        bottomOffsetPx = 0
        toolbar.setActivePicker(null)
        toolbar.setNavigationBottomPaddingEnabled(true)
        body.setEmojiInputActive(false)
        requestLayout()
    }

    fun release() {
        onHostStopped()
        ViewCompat.setOnApplyWindowInsetsListener(this, null)
        ViewCompat.setWindowInsetsAnimationCallback(this, null)
        panel.listener = null
        pickerHost.dispose()
        body.dispose()
    }

    fun setScreenPlaybackGate(gate: EmojiPlaybackGate) {
        if (playbackGateApplied && playbackGate == gate) return
        playbackGateApplied = true
        playbackGate = gate
        body.setEmojiPlaybackGate(gate)
        pickerHost.setEmojiPlaybackGate(gate)
    }

    private fun setPickerOpen(open: Boolean) {
        if (emojiRequested == open && (pickerHost.isPickerInitialized || !open)) {
            return
        }
        emojiRequested = open
        if (open) {
            bottomOffsetPx = 0
            pickerHost.ensurePicker()
            pickerHost.setEmojiPlaybackGate(playbackGate)
        }
        bottomSlot = if (
            shouldKeepEditorEmojiSlot(
                emojiRequested = open,
                composerPanel = composerController.state.panel,
                panelShouldShowPicker = panel.shouldShowPicker(),
            )
        ) BottomSlot.Emoji else BottomSlot.Closed
        updatePanelSlot()
    }

    private fun updatePanelSlot() {
        val showEmoji = bottomSlot == BottomSlot.Emoji
        val targetHeight = panel.pickerPanelHeight(defaultPickerHeight()).coerceAtLeast(1)
        val collapsedHeight = navigationBottomInsetPx.coerceAtMost(targetHeight)

        if (!showEmoji) setEmojiPlaybackActive(false)

        if (showEmoji) {
            pickerPanel.show(
                content = pickerHost,
                height = targetHeight,
                collapsedHeight = collapsedHeight,
                animateFromCollapsed = !panel.isImeVisible(),
                onShown = {
                    if (bottomSlot == BottomSlot.Emoji) setEmojiPlaybackActive(true)
                },
            )
        } else {
            setEmojiPlaybackActive(false)
            pickerPanel.hide(targetHeight, collapsedHeight) {
                updateToolbarNavigationPadding()
                requestLayout()
            }
        }
        updateToolbarNavigationPadding()
        requestLayout()
    }

    private fun setEmojiPlaybackActive(active: Boolean) {
        if (emojiPlaybackActive == active) return
        emojiPlaybackActive = active
        pickerHost.setActive(active)
    }

    private fun finishEmojiToKeyboardHandoff(mode: BottomInputPanelController.Mode) {
        if (mode != BottomInputPanelController.Mode.Keyboard ||
            emojiRequested ||
            panel.shouldShowPicker() ||
            bottomSlot != BottomSlot.Emoji
        ) {
            return
        }
        bottomSlot = BottomSlot.Closed
        setEmojiPlaybackActive(false)
        pickerPanel.hideImmediately()
    }

    private fun updateToolbarNavigationPadding() {
        toolbar.setNavigationBottomPaddingEnabled(
            panel.mode() == BottomInputPanelController.Mode.None &&
                !pickerPanel.isVisible,
        )
    }

    private fun defaultPickerHeight(): Int {
        return context.defaultPickerPanelHeight()
    }

    private enum class BottomSlot {
        Closed,
        Emoji,
    }

}
