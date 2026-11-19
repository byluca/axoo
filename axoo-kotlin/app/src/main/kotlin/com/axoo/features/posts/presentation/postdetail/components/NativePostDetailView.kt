package com.axoo.features.posts.presentation.postdetail.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.features.posts.presentation.composer.BottomInputPanelController
import com.axoo.features.posts.presentation.composer.ComposerController
import com.axoo.features.posts.presentation.postdetail.PostDetailEvent
import com.axoo.features.posts.presentation.postdetail.PostDetailUiState
import com.axoo.ui.components.AxooRefreshLayout
import com.axoo.ui.components.BottomFadeView
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Disegna la schermata Post Detail completa e gestisce composer e tastiera. */
@SuppressLint("ViewConstructor")
internal class NativePostDetailView(
    context: Context,
    private val composerController: ComposerController,
    onPayloadChanged: (String, List<MessageEntity>, Int, Int) -> Unit,
) : FrameLayout(context) {
    private var eventSink: (PostDetailEvent) -> Unit = {}
    private var bottomOffset = 0f
    private var activeImeAnimations = 0
    private val imeAnimationRunning: Boolean
        get() = activeImeAnimations > 0
    private var latestImeInsets: WindowInsetsCompat? = null
    private var released = false
    private val panel = composerController.panelController
    private val navigationBarOverlap: Int
        get() = min(panel.navBottomInset(), bottomOffset.toInt()).coerceAtLeast(0)

    private val topBar = NativePostDetailTopBarView(context)
    private val detailView = PostDetailContentView(context)
    private val refreshLayout = AxooRefreshLayout(
        context = context,
        content = detailView,
        canChildScrollUp = detailView::canScrollUp,
    ).apply {
        setOnRefreshListener { eventSink(PostDetailEvent.RefreshRequested) }
    }
    private val composerBackdrop = BottomFadeView(context)
    private val editingScrim = View(context).apply {
        setBackgroundColor(NativeColors.scrim)
        alpha = EDITING_SCRIM_ALPHA
        isClickable = true
        isFocusable = true
        visibility = View.GONE
    }
    private val composerPanel = CommentComposerPanelView(
        context = context,
        composerController = composerController,
        onPayloadChanged = onPayloadChanged,
        onSelectionChanged = { start, end ->
            eventSink(PostDetailEvent.ReplySelectionChanged(start, end))
        },
        onInputActivated = ::requestKeyboardFromComposer,
        onEmojiToggle = ::togglePicker,
    )
    private var bottomAnimator: ValueAnimator? = null
    private val bottomInterpolator = PathInterpolator(0.18f, 0.72f, 0.22f, 1f)

    init {
        setBackgroundColor(NativeColors.surface)
        clipChildren = false
        clipToPadding = false

        topBar.setOnBack {
            eventSink(PostDetailEvent.BackTapped)
        }
        composerPanel.setCallbacks(
            onSend = { eventSink(PostDetailEvent.SendReplyTapped) },
            onCancelReply = { eventSink(PostDetailEvent.CancelReplyTapped) },
        )
        detailView.setCallbacks(
            onUpvote = { eventSink(PostDetailEvent.CommentUpvoteTapped(it)) },
            onDownvote = { eventSink(PostDetailEvent.CommentDownvoteTapped(it)) },
            onDelete = { eventSink(PostDetailEvent.CommentDeleteTapped(it)) },
            onPostUpvote = { eventSink(PostDetailEvent.PostUpvoteTapped) },
            onPostDownvote = { eventSink(PostDetailEvent.PostDownvoteTapped) },
            onPollVote = { option -> eventSink(PostDetailEvent.PollOptionTapped(option)) },
            onMenu = { eventSink(PostDetailEvent.CommentMenuTapped(it)) },
            onReply = { eventSink(PostDetailEvent.CommentReplyTapped(it)) },
            onPostMenu = { eventSink(PostDetailEvent.PostMenuTapped(it)) },
            onAuthor = { eventSink(PostDetailEvent.AuthorTapped(it)) },
        )

        addView(topBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(refreshLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(composerBackdrop, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(editingScrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(composerPanel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        panel.listener = object : BottomInputPanelController.Listener {
            override fun onPanelStateChanged(
                targetBottom: Int,
                mode: BottomInputPanelController.Mode,
            ) {
                val showPicker = panel.shouldShowPicker()
                val pickerPanelHeight = panel.pickerPanelHeight(defaultPickerHeight())
                composerPanel.setPickerPanelHeight(pickerPanelHeight)
                when {
                    showPicker -> {
                        snapBottomTo(0f)
                        composerPanel.setPickerOpen(
                            open = true,
                            animate = !panel.isImeVisible(),
                            emojiRequested = panel.pickerOpen(),
                            collapsedHeightPx = panel.navBottomInset(),
                        )
                    }
                    mode == BottomInputPanelController.Mode.Keyboard -> {
                        if (!imeAnimationRunning) {
                            snapBottomTo(targetBottom.toFloat())
                        }
                        composerPanel.setPickerOpen(open = false, animate = false)
                    }
                    else -> {
                        val wasPickerOpen = composerPanel.isPickerOpen()
                        composerPanel.setPickerOpen(
                            open = false,
                            animate = true,
                            collapsedHeightPx = targetBottom,
                            onHidden = {
                                if (!panel.shouldShowPicker()) {
                                    snapBottomTo(targetBottom.toFloat())
                                }
                            },
                        )
                        if (
                            !imeAnimationRunning &&
                            !wasPickerOpen &&
                            !composerPanel.isPickerAnimationRunning()
                        ) {
                            animateBottom(targetBottom.toFloat())
                        }
                    }
                }
                requestLayout()
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            if (imeAnimationRunning) {
                latestImeInsets = insets
            } else {
                applyWindowInsets(insets)
            }
            insets
        }
        ViewCompat.setWindowInsetsAnimationCallback(
            this,
            object : WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE,
            ) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() == 0) return
                    activeImeAnimations++
                    panel.onImeAnimationStarted()
                    latestImeInsets = null
                    bottomAnimator?.cancel()
                    bottomAnimator = null
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>,
                ): WindowInsetsCompat {
                    if (imeAnimationRunning) {
                        latestImeInsets = insets
                    }
                    if (imeAnimationRunning && !panel.shouldShowPicker()) {
                        val navigationBottom =
                            insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                        val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                        bottomOffset = max(navigationBottom, imeBottom).toFloat()
                        requestLayout()
                    }
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() == 0) return
                    if (activeImeAnimations > 0) activeImeAnimations--
                    val settledInsets = latestImeInsets
                        ?: ViewCompat.getRootWindowInsets(this@NativePostDetailView)
                    settledInsets?.let(::applyWindowInsets)
                    panel.onImeAnimationEnd()
                    if (imeAnimationRunning) return
                    latestImeInsets = null
                    if (!panel.shouldShowPicker()) {
                        snapBottomTo(
                            panel.targetBottomOffset(defaultPickerHeight()).toFloat(),
                        )
                    }
                }
            },
        )
        ViewCompat.requestApplyInsets(this)
    }

    fun setEventSink(callback: (PostDetailEvent) -> Unit) {
        if (released) return
        eventSink = callback
    }

    fun setOnMediaRequested(callback: (Boolean) -> Unit) { composerPanel.setOnMediaRequested(callback) }

    fun render(
        uiState: PostDetailUiState,
    ) {
        if (released) return
        setBackgroundColor(NativeColors.surface)
        val editing = uiState.editingCommentId != null
        topBar.bind(editing)
        editingScrim.visibility = if (editing) View.VISIBLE else View.GONE
        refreshLayout.isEnabled = !editing
        refreshLayout.isRefreshing = uiState.isRefreshing
        detailView.render(
            post = uiState.post,
            comments = uiState.comments,
            isCommentsLoading = uiState.isCommentsLoading,
            isCommentsError = uiState.isCommentsError,
        )
        val replyTarget = uiState.comments.firstOrNull { it.id == uiState.replyDraft.replyToCommentId }?.comment
        composerPanel.bind(
            text = uiState.replyDraft.text,
            entities = uiState.replyDraft.entities,
            pickerOpen = panel.shouldShowPicker(),
            editing = editing,
            saving = uiState.isSavingCommentEdit,
            replyNickname = uiState.replyDraft.replyToCommentId?.let {
                replyTarget?.nickname
                    ?.takeIf { it.isNotBlank() } ?: "commento"
            },
            replyComment = replyTarget,
        )
        if (editing) {
            editingScrim.bringToFront()
            composerPanel.bringToFront()
        }
    }

    fun focusCommentEditor(cursor: Int? = null) {
        if (composerPanel.isPickerAnimationRunning()) return
        detailView.prepareForInputTransition()
        if (cursor == null) composerPanel.moveCursorToEnd() else composerPanel.moveCursorTo(cursor)
        composerController.requestKeyboard()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val exactWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val availableHeight = height.coerceAtLeast(0)
        val atMostHeight = MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.AT_MOST)

        topBar.measure(exactWidth, atMostHeight)
        composerPanel.measure(exactWidth, atMostHeight)
        detailView.setComposerBottomPadding(composerPanel.inputBarHeight + navigationBarOverlap)
        val contentHeight = (
            availableHeight - topBar.measuredHeight -
                composerPanel.measuredHeight + composerPanel.inputBarHeight - bottomOffset.toInt() +
                navigationBarOverlap
            )
            .coerceAtLeast(0)
        refreshLayout.measure(
            exactWidth,
            MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY),
        )
        editingScrim.measure(
            exactWidth,
            MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY),
        )
        composerBackdrop.measure(
            exactWidth,
            MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.AT_MOST),
        )

        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        val topHeight = topBar.measuredHeight
        val composerHeight = composerPanel.measuredHeight
        val composerBottom = (height - bottomOffset.toInt()).coerceAtLeast(topHeight)
        val composerTop = (composerBottom - composerHeight).coerceAtLeast(topHeight)
        val contentBottom = composerTop + composerPanel.inputBarHeight + navigationBarOverlap
        val backdropBottom = if (bottomOffset > panel.navBottomInset()) composerBottom else contentBottom

        topBar.layout(0, 0, width, topHeight)
        refreshLayout.layout(0, topHeight, width, contentBottom)
        composerBackdrop.layout(0, backdropBottom - composerBackdrop.measuredHeight, width, backdropBottom)
        editingScrim.layout(0, topHeight, width, contentBottom)
        composerPanel.layout(0, composerTop, width, composerBottom)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    fun onHostStarted() {
        if (released) return
        composerPanel.setEmojiInputActive(true)
    }

    fun onHostStopped() {
        if (panel.pickerOpen()) {
            composerController.closePanel(focusKeyboardNext = false)
        } else {
            composerPanel.dismissPickerLifecycle()
            composerController.dismissAll()
        }
        composerPanel.setEmojiInputActive(false)
    }

    /** Phase 2 — route the navigation gate to the detail recycler. */
    fun setScreenPlaybackGate(gate: EmojiPlaybackGate) {
        if (released) return
        detailView.setScreenPlaybackGate(gate)
        composerPanel.setScreenPlaybackGate(gate)
    }

    fun releaseViewResourcesForTrim() {
        releaseViewResources()
    }

    fun release() {
        releaseViewResources()
    }

    private fun releaseViewResources() {
        if (released) return
        released = true
        onHostStopped()
        ViewCompat.setOnApplyWindowInsetsListener(this, null)
        ViewCompat.setWindowInsetsAnimationCallback(this, null)
        panel.listener = null
        composerController.keyboardAnchor = null
        composerController.changeListener = {}
        detailView.release()
        bottomAnimator?.cancel()
        bottomAnimator = null
        activeImeAnimations = 0
        panel.resetImeAnimationState()
        latestImeInsets = null
        composerPanel.dispose()
        eventSink = {}
        removeAllViews()
    }

    private fun togglePicker() {
        if (composerPanel.isPickerAnimationRunning()) return
        detailView.prepareForInputTransition()
        composerController.toggleEmojiPanel()
    }

    private fun requestKeyboardFromComposer() {
        if (composerPanel.isPickerAnimationRunning()) return
        detailView.prepareForInputTransition()
        composerController.requestKeyboard()
    }

    private fun applyWindowInsets(insets: WindowInsetsCompat) {
        val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        composerPanel.setNavigationBottomInset(nav)
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
    }

    fun dismissPickerIfOpen(): Boolean {
        if (!panel.pickerOpen()) return false
        composerController.closePanel(focusKeyboardNext = false)
        return true
    }

    fun scrollToComment(id: String) = detailView.scrollToComment(id)

    fun scrollToBottom() {
        detailView.scrollToBottom()
    }

    private fun animateBottom(target: Float) {
        if (height == 0) {
            snapBottomTo(target)
        } else {
            animateBottomTo(target, resources.displayMetrics.heightPixels * 0.45f)
        }
    }

    private fun snapBottomTo(target: Float) {
        bottomAnimator?.cancel()
        bottomAnimator = null
        bottomOffset = target
        requestLayout()
    }

    private fun animateBottomTo(target: Float, distanceBase: Float) {
        if (bottomOffset == target) return
        bottomAnimator?.cancel()
        val start = bottomOffset
        val distance = abs(target - start)
        bottomAnimator = ValueAnimator.ofFloat(start, target).apply {
            duration = (170L + distance / distanceBase.coerceAtLeast(1f) * 110L)
                .toLong()
                .coerceIn(170L, 300L)
            interpolator = bottomInterpolator
            addUpdateListener { animation ->
                bottomOffset = animation.animatedValue as Float
                requestLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (bottomAnimator === animation) bottomAnimator = null
                }
            })
            start()
        }
    }

    private fun defaultPickerHeight(): Int {
        val screenHeight = resources.displayMetrics.heightPixels
        return max(dp(240), min(dp(320), (screenHeight * 0.34f).roundToInt()))
    }

    private companion object {
        const val EDITING_SCRIM_ALPHA = 0.22f
    }
}
