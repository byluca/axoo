/** Collega Post Detail a navigazione, ViewModel e menu. */
package com.axoo.features.posts.presentation.postdetail

import android.annotation.SuppressLint
import com.axoo.features.moderation.presentation.showModeratorHideConfirmation
import android.content.Context
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.axoo.BuildConfig
import com.axoo.application.navigation.host.BackHandler
import com.axoo.application.navigation.route.NativeScreen
import com.axoo.application.navigation.route.Route
import com.axoo.core.audio.AppSounds
import com.axoo.core.haptics.Haptics
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.features.moderation.presentation.ModerationViewModel
import com.axoo.features.posts.domain.ImageMeta
import com.axoo.features.posts.domain.RemoteMediaType
import com.axoo.features.posts.presentation.media.RemoteMediaViewModel
import com.axoo.features.posts.presentation.media.components.RemoteMediaPickerSheet
import com.axoo.features.posts.domain.Comment
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.presentation.composer.ComposerController
import com.axoo.features.posts.presentation.postdetail.components.CommentOptionsSheet
import com.axoo.features.posts.presentation.postdetail.components.NativePostDetailTopBarView
import com.axoo.features.posts.presentation.postdetail.components.NativePostDetailView
import com.axoo.features.posts.presentation.postdetail.components.PostDetailCommentsHeaderView
import com.axoo.features.posts.presentation.sheets.PostOptionsSheet
import com.axoo.ui.components.NativeSkeletonCardView
import com.axoo.ui.components.SkeletonType
import com.axoo.ui.sheets.OverlayCard
import com.axoo.ui.sheets.OverlayCardAction
import com.axoo.ui.sheets.OverlayCopy
import com.axoo.features.posts.presentation.report.ReportSheet
import com.axoo.ui.state.UiState
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor")
internal class PostDetailHost(
    context: Context,
    postId: String,
    private val viewModel: CommentsViewModel,
    private val remoteMediaViewModel: RemoteMediaViewModel,
    private val moderationViewModel: ModerationViewModel,
    private val lifecycleOwner: LifecycleOwner,
    targetCommentId: String? = null,
) : FrameLayout(context), BackHandler, NativeScreen {
    override val route: Route = Route.PostDetail(postId, commentId = targetCommentId)
    private var pendingCommentId = targetCommentId
    private var notificationRefreshStarted = false
    private var onBackOut: () -> Unit = {}
    private var onEditOut: (Post) -> Unit = {}
    private var onDeletedOut: (String) -> Unit = {}

    // Phase 3 — shell. Created in constructor. No emoji catalog, no composer, no
    // RecyclerView. Just the topbar and a background so the push animation can
    // start with a stable visual.
    private val shellTopBar = NativePostDetailTopBarView(context)
    private val shellPostSkeleton = NativeSkeletonCardView(context, rows = 1)
    private val shellCommentsSkeleton = NativeSkeletonCardView(
        context,
        rows = 2,
        type = SkeletonType.Comment,
    )
    private val shellCommentsHeader = PostDetailCommentsHeaderView(context)
    private val shellSkeletons = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, context.dp(4f).toInt(), 0, 0)
        addView(
            shellPostSkeleton,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        addView(
            shellCommentsHeader,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dp(8)
                bottomMargin = context.dp(4)
            },
        )
        addView(
            shellCommentsSkeleton,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
    }

    // Phase 3 — heavy content. Built only after the transition settles, via a
    // cancellable chunked sequence kicked off from onScreenFullyVisible.
    private var composerController: ComposerController? = null
    private var rootView: NativePostDetailView? = null
    private var heavyInitGeneration = 0

    private var mediaSheet: RemoteMediaPickerSheet? = null
    private var mediaObserver: Job? = null
    private var menuPost: Post? = null
    private var menuComment: Comment? = null
    private var lastEditFinishedSignal = 0
    private var focusCommentEditorAfterMenuDismiss = false

    private var lifecycleObserver: DefaultLifecycleObserver? = null
    private var observerStarted: Boolean = false
    private var observerJob: Job? = null
    private var screenFullyVisible: Boolean = false

    private var postMenuHandle: PostOptionsSheet.Handle? = null
    private var commentMenuHandle: CommentOptionsSheet.Handle? = null
    private var reportHandle: ReportSheet.Handle? = null

    // Latest gate decided by the navigation lifecycle. Stashed here so that when
    // rootView is finally created mid-chunk, we can push the current gate without
    // racing the lifecycle.
    private var pendingGate: EmojiPlaybackGate = EmojiPlaybackGate.Inactive

    // Chunked heavy-init state.
    private var heavyInitStarted: Boolean = false
    private var heavyInitCancelled: Boolean = false
    private val pendingChunks: MutableList<Runnable> = mutableListOf()

    // Phase 3 debug telemetry. All gated on BuildConfig.DEBUG so release builds
    // pay zero cost (the `if (BuildConfig.DEBUG)` is a compile-time constant
    // branch the Kotlin compiler strips when false).
    private var fullyVisibleAtMs: Long = 0L

    /**
     * Phase 3 — exposed so callers/tests can confirm the heavy subtree has landed.
     * False during the shell-only phase (constructor through onScreenFullyVisible).
     */
    val isHeavyContentReady: Boolean
        get() = rootView != null

    init {
        val ctorStart = if (BuildConfig.DEBUG) SystemClock.uptimeMillis() else 0L
        if (BuildConfig.DEBUG) Trace.beginSection("PostDetailHost.ctor")
        setBackgroundColor(NativeColors.surface)
        shellTopBar.setOnBack { handleEvent(PostDetailEvent.BackTapped) }
        addView(
            shellSkeletons,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        addView(
            shellTopBar,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        shellTopBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            shellSkeletons.translationY = (bottom - top).toFloat()
        }
        
        installLifecycleObserver(lifecycleOwner)

        if (BuildConfig.DEBUG) {
            Trace.endSection()
            Log.d(PHASE3_TAG, "ctor postId=$postId took=${SystemClock.uptimeMillis() - ctorStart}ms")
        }
    }

    fun setCallbacks(
        onBack: () -> Unit,
        onEdit: (Post) -> Unit = {},
        onDeleted: (String) -> Unit = {},
        onAuthor: (String) -> Unit = {},
    ) {
        onBackOut = onBack
        onEditOut = onEdit
        onDeletedOut = onDeleted
        onAuthorOut = onAuthor
    }

    private var onAuthorOut: (String) -> Unit = {}

    override fun onBackPressed(): Boolean {
        if (rootView?.dismissPickerIfOpen() == true) return true
        if (viewModel.state.value.editingComment != null) {
            cancelCommentEdit()
            return true
        }
        return false
    }

    private fun startObserver(owner: LifecycleOwner, vm: CommentsViewModel) {
        if (observerStarted) return
        if (!screenFullyVisible) return
        val view = rootView ?: return
        observerStarted = true
        observerJob = owner.lifecycleScope.launch {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    val ui = buildUiState(state)
                    view.render(uiState = ui)
                    pendingCommentId?.let { id ->
                        if (!ui.isCommentsLoading && !ui.isCommentsError && !state.isRefreshing) {
                            view.scrollToComment(id)
                            pendingCommentId = null
                        }
                    }
                    if (lastEditFinishedSignal != state.editFinishedSignal) {
                        lastEditFinishedSignal = state.editFinishedSignal
                        composerController?.hideKeyboard()
                    }
                    state.replyError?.let {
                        OverlayCard.error(this@PostDetailHost, it)
                        vm.clearReplyError()
                    }
                }
            }
        }
    }

    private fun stopObserver() {
        observerJob?.cancel()
        observerJob = null
        observerStarted = false
    }

    private fun installLifecycleObserver(owner: LifecycleOwner) {
        if (lifecycleObserver != null) return
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (screenFullyVisible) {
                    rootView?.onHostStarted()
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                rootView?.onHostStopped()
            }
        }
        lifecycleObserver = observer
        owner.lifecycle.addObserver(observer)
    }

    override fun onScreenTransitionStart(isOpening: Boolean, isBackward: Boolean) {
        applyGate(EmojiPlaybackGate.ActiveFrozen)
    }

    override fun onInteractiveTransitionStart(isOpening: Boolean) {
        // Lo swipe-back è gestito dal NavigationHost prima che Android consegni il
        // gesto alla IME. Chiudiamo quindi il composer subito, così la tastiera
        // non resta visibile sopra la schermata a cui si sta tornando.
        if (!isOpening) composerController?.dismissAll()
        applyGate(EmojiPlaybackGate.InteractiveTransition)
    }

    override fun onScreenFullyVisible() {
        if (pendingCommentId != null && !notificationRefreshStarted) {
            notificationRefreshStarted = true
            viewModel.refreshDetail(showIndicator = false)
        }
        // Phase 3 — transition has settled. Unfreeze and start the chunked init
        // sequence; each chunk runs on a subsequent frame and is cancellable.
        if (BuildConfig.DEBUG) {
            fullyVisibleAtMs = SystemClock.uptimeMillis()
            Log.d(PHASE3_TAG, "onScreenFullyVisible — scheduling heavy init chunks")
        }
        screenFullyVisible = true
        viewModel.refreshPostIfLoaded()
        applyGate(EmojiPlaybackGate.FullyVisible)
        scheduleHeavyContentInitChunks()
        if (isHeavyContentReady) {
            startObserver(lifecycleOwner, viewModel)
        }
    }

    override fun onScreenHidden() {
        screenFullyVisible = false
        stopObserver()
        applyGate(EmojiPlaybackGate.Inactive)
        rootView?.onHostStopped()
    }

    override fun onScreenDestroyed() {
        mediaSheet?.dismiss()
        mediaObserver?.cancel()
        screenFullyVisible = false
        cancelPendingChunks()
        heavyInitGeneration += 1
        stopObserver()
        lifecycleObserver?.let { observer ->
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
        lifecycleObserver = null
        dismissViewBoundSheetsAndMenus()
        rootView?.release()
        rootView = null
        composerController = null
    }

    // ── Heavy init chunks ─────────────────────────────────────────────────────

    private fun applyGate(gate: EmojiPlaybackGate) {
        pendingGate = gate
        rootView?.setScreenPlaybackGate(gate)
    }

    private fun scheduleHeavyContentInitChunks() {
        if (heavyInitStarted) return
        heavyInitStarted = true
        heavyInitCancelled = false
        // Phase 3 profiling note (on-device, debug build, Pixel-class):
        //   ctor ≈ 0 ms, pre-cache chunk1 ≈ 73–141 ms, chunk2 ≈ 6–22 ms,
        //   chunk3 ≈ 0 ms, chunk4 ≈ 1–2 ms,
        //   first render complete ≈ 85–167 ms from onScreenFullyVisible.
        // The push animation itself is no longer blocked since heavy work runs
        // strictly after onScreenFullyVisible.
        val generation = ++heavyInitGeneration
        heavyChunkBuildBaseDeps(generation)
    }

    private fun enqueueChunk(label: String, action: () -> Unit) {
        val runnable = object : Runnable {
            override fun run() {
                pendingChunks.remove(this)
                if (heavyInitCancelled) return
                if (BuildConfig.DEBUG) {
                    Trace.beginSection("PostDetailHost.$label")
                    val start = SystemClock.uptimeMillis()
                    try {
                        action()
                    } finally {
                        val took = SystemClock.uptimeMillis() - start
                        Trace.endSection()
                        Log.d(PHASE3_TAG, "$label took=${took}ms")
                    }
                } else {
                    action()
                }
            }
        }
        pendingChunks += runnable
        post(runnable)
    }

    private fun cancelPendingChunks() {
        heavyInitCancelled = true
        pendingChunks.forEach { removeCallbacks(it) }
        pendingChunks.clear()
        heavyInitGeneration += 1
    }

    private fun dismissViewBoundSheetsAndMenus() {
        postMenuHandle?.dismiss()
        commentMenuHandle?.dismiss()
        reportHandle?.dismiss()
        postMenuHandle = null
        commentMenuHandle = null
        reportHandle = null
        menuPost = null
        menuComment = null
    }

    private fun heavyChunkBuildBaseDeps(generation: Int) {
        if (generation != heavyInitGeneration || heavyInitCancelled) return

        val applyStart = if (BuildConfig.DEBUG) SystemClock.uptimeMillis() else 0L
        if (composerController == null) {
            val composerStart = if (BuildConfig.DEBUG) SystemClock.uptimeMillis() else 0L
            composerController = ComposerController(context)
            if (BuildConfig.DEBUG) {
                Log.d(
                    PHASE3_TAG,
                    "chunk1.apply.composerController took=${SystemClock.uptimeMillis() - composerStart}ms",
                )
            }
        }
        if (BuildConfig.DEBUG) {
            val applyTook = SystemClock.uptimeMillis() - applyStart
            Log.d(
                PHASE3_TAG,
                "chunk1.apply.total took=${applyTook}ms",
            )
            Log.d(
                PHASE3_TAG,
                "chunk1.baseDeps.applyMain took=${applyTook}ms",
            )
        }

        if (generation != heavyInitGeneration || heavyInitCancelled) return

        enqueueChunk("chunk2.attachRootView", ::heavyChunkAttachRootView)
        enqueueChunk("chunk3.renderAndGate", ::heavyChunkRenderAndGate)
        enqueueChunk("chunk4.startObserver", ::heavyChunkStartObserver)
    }

    private fun heavyChunkAttachRootView() {
        if (rootView != null) return
        val composer = composerController ?: return
        val newView = NativePostDetailView(
            context = context,
            composerController = composer,
            onPayloadChanged = { text, entities, start, end ->
                handleEvent(PostDetailEvent.ReplyDraftChanged(text, entities, start, end))
            },
        )
        composer.keyboardAnchor = this
        newView.setEventSink(::handleEvent)
        newView.setOnMediaRequested(::openMediaPicker)
        rootView = newView
        addView(
            newView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        // Shell topbar is now covered by the heavy rootView's own topbar (same
        // class, visually identical). Hide it to free measure/layout cost.
        shellTopBar.visibility = GONE
        shellSkeletons.visibility = GONE
    }

    private fun openMediaPicker(sticker: Boolean) {
        if (viewModel.state.value.editingComment != null || mediaSheet != null) return
        composerController?.dismissAll()
        val type = if (sticker) RemoteMediaType.Sticker else RemoteMediaType.Gif
        remoteMediaViewModel.open(type)
        val sheet = RemoteMediaPickerSheet(context,
            onMediaSelected = { media ->
                remoteMediaViewModel.rememberSelection(media)
                Haptics.commentSent(this)
                AppSounds.commentSent(context)
                val rootComment = viewModel.state.value.replyDraft.replyToCommentId == null
                viewModel.sendMedia(ImageMeta(media.mediaUrl, if (media.type == RemoteMediaType.Gif) media.loadingPreviewUrl ?: media.mediaUrl else media.previewUrl, media.width, media.height,
                    if (sticker) "sticker" else "gif", media.mimeType ?: "image/gif", media.previewFallbackUrl, media.provider, media.id))
                renderUi()
                if (rootComment) rootView?.scrollToBottom()
            },
            onSearchQueryChanged = remoteMediaViewModel::search,
            onLoadMore = remoteMediaViewModel::loadMore,
            onRetry = remoteMediaViewModel::retry,
            onDismissed = { mediaObserver?.cancel(); mediaObserver = null; mediaSheet = null },
        )
        mediaSheet = sheet
        sheet.bind(remoteMediaViewModel.state.value)
        mediaObserver = lifecycleOwner.lifecycleScope.launch {
            remoteMediaViewModel.state.collect { state ->
                if (state.type == type) sheet.bind(state)
            }
        }
        sheet.presentOn(this)
    }

    private fun heavyChunkRenderAndGate() {
        val view = rootView ?: return
        view.render(
            uiState = buildUiState(),
        )
        view.setScreenPlaybackGate(pendingGate)
        if (screenFullyVisible) view.onHostStarted()
        if (BuildConfig.DEBUG && fullyVisibleAtMs > 0L) {
            val elapsed = SystemClock.uptimeMillis() - fullyVisibleAtMs
            Log.d(PHASE3_TAG, "first render complete elapsedFromFullyVisible=${elapsed}ms")
        }
    }

    private fun heavyChunkStartObserver() {
        if (!screenFullyVisible) return
        startObserver(lifecycleOwner, viewModel)
    }

    private companion object {
        private const val PHASE3_TAG = "PostDetailPhase3"
    }

    // ── Event dispatch ────────────────────────────────────────────────────────

    private fun handleEvent(event: PostDetailEvent) {
        val vm = viewModel
        if (!vm.allows(event)) return
        when (event) {
            PostDetailEvent.BackTapped -> {
                when {
                    viewModel.state.value.editingComment != null -> cancelCommentEdit()
                    rootView?.dismissPickerIfOpen() == true -> Unit
                    else -> onBackOut()
                }
            }
            PostDetailEvent.RefreshRequested -> vm.refreshDetail()
            is PostDetailEvent.AuthorTapped -> onAuthorOut(event.userId)
            PostDetailEvent.PostUpvoteTapped -> vm.votePost("upvote")
            PostDetailEvent.PostDownvoteTapped -> vm.votePost("downvote")
            is PostDetailEvent.PollOptionTapped -> vm.votePoll(event.optionId) {
                showActionError("Voto non inviato. Riprova.")
            }
            PostDetailEvent.SendReplyTapped -> sendReply()
            is PostDetailEvent.ReplyDraftChanged -> vm.updateReplyDraft(
                event.text, event.entities, event.selectionStart, event.selectionEnd,
            )
            is PostDetailEvent.ReplySelectionChanged -> vm.updateReplySelection(event.start, event.end)
            is PostDetailEvent.CommentReplyTapped -> {
                if (vm.beginReply(event.comment)) { renderUi(); rootView?.focusCommentEditor() }
            }
            PostDetailEvent.CancelReplyTapped -> vm.cancelReply()
            is PostDetailEvent.CommentUpvoteTapped -> vm.voteComment(event.commentId, "upvote")
            is PostDetailEvent.CommentDownvoteTapped -> vm.voteComment(event.commentId, "downvote")
            is PostDetailEvent.CommentDeleteTapped -> vm.deleteComment(event.commentId)
            is PostDetailEvent.CommentMenuTapped -> {
                menuPost = null
                menuComment = event.comment
                onMenuChanged()
            }
            is PostDetailEvent.PostMenuTapped -> {
                menuComment = null
                menuPost = event.post
                onMenuChanged()
            }
        }
    }

    private fun sendReply() {
        val isRootComment = viewModel.state.value.replyDraft.replyToCommentId == null
        when (viewModel.submitReply()) {
            CommentsViewModel.ReplySubmission.Sent -> {
                renderUi()
                if (isRootComment) rootView?.scrollToBottom()
                Haptics.commentSent(this)
                AppSounds.commentSent(context)
            }
            CommentsViewModel.ReplySubmission.EditSaving -> Haptics.confirm(this)
            CommentsViewModel.ReplySubmission.EditFinished -> composerController?.hideKeyboard()
            null -> Unit
        }
    }

    private fun startCommentEdit(comment: Comment) {
        if (!viewModel.beginEdit(comment)) return
        focusCommentEditorAfterMenuDismiss = true
        renderUi()
    }

    private fun cancelCommentEdit() { viewModel.cancelEdit() }

    // ── Menu sheet sync ───────────────────────────────────────────────────────

    private fun onMenuChanged() {
        syncPostMenuSheet()
        syncCommentMenuSheet()
    }

    private fun syncPostMenuSheet() {
        val target = menuPost
        val currentUserId = viewModel.state.value.currentUserId
        val isOwn = currentUserId != null && target?.userId == currentUserId

        if (target != null && postMenuHandle == null) {
            postMenuHandle = PostOptionsSheet.present(
                anchor = this,
                isOwnPost = isOwn,
                canModerate = !isOwn && viewModel.isModerator(),
                canEdit = target.poll == null,
                onReport = { showReportSheet("post", target.id) },
                onHide = { viewModel.hidePost(target.id) { result ->
                    result.onSuccess { onDeletedOut(target.id); onBackOut() }
                        .onFailure { OverlayCard.error(this, OverlayCopy.operationError) }
                } },
                onBlock = { showBlockUserConfirmation(target.userId) },
                onEdit = { if (isOwn && target.poll == null) onEditOut(target) },
                onDelete = {
                    if (isOwn) showDeleteConfirmation()
                },
                onModerationRemove = {
                    showModeratorHideConfirmation(
                        anchor = this,
                        moderationViewModel = moderationViewModel,
                        targetType = "post",
                        targetId = target.id,
                        onSuccess = {
                            onDeletedOut(target.id)
                            onBackOut()
                        },
                    )
                },
                onDismissed = {
                    postMenuHandle = null
                    menuPost = null
                    onMenuChanged()
                },
            )
        } else if (target == null) {
            postMenuHandle?.dismiss()
        }
    }

    private fun showDeleteConfirmation() {
        OverlayCard.present(
            anchor = this,
            title = OverlayCopy.deletePostTitle,
            message = OverlayCopy.deleteDetailPostMessage,
            primaryAction = OverlayCardAction(
                text = OverlayCopy.delete,
                destructive = true,
                onClick = ::deleteCurrentPost,
            ),
            secondaryAction = OverlayCardAction(text = OverlayCopy.cancel, onClick = {}),
        )
    }

    private fun showBlockUserConfirmation(userId: String) {
        OverlayCard.present(
            anchor = this,
            title = OverlayCopy.blockTitle,
            message = OverlayCopy.blockMessage,
            primaryAction = OverlayCardAction(
                text = OverlayCopy.block,
                destructive = true,
                onClick = {
                    viewModel.blockUser(userId) { result ->
                        result.onSuccess { onBackOut() }.onFailure { showActionError(OverlayCopy.operationError) }
                    }
                },
            ),
            secondaryAction = OverlayCardAction(text = OverlayCopy.cancel, onClick = {}),
        )
    }

    private fun deleteCurrentPost() {
        val vm = viewModel
        vm.deletePost { result ->
            result.fold(
                onSuccess = {
                    onDeletedOut(vm.postId)
                    onBackOut()
                },
                onFailure = { showActionError(OverlayCopy.deleteError) },
            )
        }
    }

    private fun showActionError(message: String) { OverlayCard.error(this, message) }

    private fun syncCommentMenuSheet() {
        val target = menuComment
        val currentUserId = viewModel.state.value.currentUserId
        val isOwn = currentUserId != null && target?.userId == currentUserId

        if (target != null && commentMenuHandle == null) {
            commentMenuHandle = CommentOptionsSheet.present(
                anchor = this,
                isOwnComment = isOwn,
                canModerate = !isOwn && viewModel.isModerator(),
                canEdit = target.canEdit,
                onReport = { showReportSheet("comment", target.id) },
                onBlock = { showBlockUserConfirmation(target.userId) },
                onEdit = {
                    if (isOwn) startCommentEdit(target)
                },
                onDelete = {
                    if (isOwn) showDeleteCommentConfirmation(target.id)
                },
                onModerationRemove = {
                    showModeratorHideConfirmation(
                        anchor = this,
                        moderationViewModel = moderationViewModel,
                        targetType = "comment",
                        targetId = target.id,
                        onSuccess = { viewModel.loadComments() },
                    )
                },
                onDismissed = {
                    commentMenuHandle = null
                    menuComment = null
                    onMenuChanged()
                    if (focusCommentEditorAfterMenuDismiss) {
                        focusCommentEditorAfterMenuDismiss = false
                        post { rootView?.focusCommentEditor() }
                    }
                },
            )
        } else if (target == null) {
            commentMenuHandle?.dismiss()
        }
    }

    private fun showDeleteCommentConfirmation(commentId: String) {
        OverlayCard.present(
            anchor = this,
            title = OverlayCopy.deleteCommentTitle,
            message = OverlayCopy.deleteCommentMessage,
            primaryAction = OverlayCardAction(
                text = OverlayCopy.delete,
                destructive = true,
                onClick = {
                    handleEvent(PostDetailEvent.CommentDeleteTapped(commentId))
                },
            ),
            secondaryAction = OverlayCardAction(text = OverlayCopy.cancel, onClick = {}),
        )
    }

    private fun showReportSheet(targetType: String, targetId: String) {
        if (reportHandle != null) return
        reportHandle = ReportSheet.present(
            anchor = this,
            onSubmit = { reason ->
                val handle = reportHandle ?: return@present
                val vm = viewModel
                vm.report(
                    targetType = targetType,
                    targetId = targetId,
                    reason = reason.apiValue,
                    onSuccess = handle::complete,
                    onFailure = { _ ->
                        handle.showError(OverlayCopy.reportError)
                    },
                )
            },
            onDismissed = { reportHandle = null },
        )
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private fun renderUi() {
        // Phase 3 — silently skip rendering while we're still in the shell. The
        // chunked init will run its own initial render once rootView lands.
        val view = rootView ?: return
        view.render(
            uiState = buildUiState(viewModel.state.value),
        )
    }

    private fun buildUiState(state: CommentsState = CommentsState()): PostDetailUiState {
        val postState = state.post
        val commentsState = state.comments
        val commentsSuccess = commentsState as? UiState.Success
        return PostDetailUiState(
            post = (postState as? UiState.Success)?.data,
            comments = commentsSuccess?.data ?: emptyList(),
            currentUserId = state.currentUserId,
            isCommentsLoading = commentsState is UiState.Loading,
            isCommentsError = commentsState is UiState.Error,
            isRefreshing = state.isPullRefreshing,
            replyDraft = state.replyDraft,
            editingCommentId = state.editingComment?.id,
            isSavingCommentEdit = state.isSavingCommentEdit,
        )
    }
}
