/*
 * Coordina l'editor condiviso tra creazione e modifica di un post.
 *
 * Collega eventi, tastiera, Back e bottom sheet al ViewModel.
 * La parte grafica vive in NativePostEditorView.kt.
 */
package com.axoo.features.posts.presentation.posteditor

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.axoo.application.navigation.host.BackHandler
import com.axoo.application.navigation.route.NativeScreen
import com.axoo.application.navigation.route.Route
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.domain.RemoteMediaType
import com.axoo.features.posts.presentation.composer.ComposerController
import com.axoo.features.posts.presentation.media.RemoteMediaState
import com.axoo.features.posts.presentation.media.RemoteMediaViewModel
import com.axoo.features.posts.presentation.media.components.MediaPickerMode
import com.axoo.features.posts.presentation.posteditor.components.DiscardPostSheet
import com.axoo.features.posts.presentation.posteditor.components.FlairPickerSheet
import com.axoo.features.posts.presentation.posteditor.components.MediaItem
import com.axoo.features.posts.presentation.posteditor.components.NativePostEditorView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor")
internal class PostEditorHost(
    context: Context,
    private val mode: PostEditorMode,
    private val viewModel: PostEditorViewModel,
    private val remoteMediaViewModel: RemoteMediaViewModel,
    private val lifecycleOwner: LifecycleOwner,
) : FrameLayout(context), BackHandler, NativeScreen {
    override val route: Route = when (mode) {
        PostEditorMode.Create -> Route.PostEditor
        is PostEditorMode.Edit -> Route.EditPost(mode.postId)
    }
    private var onBackOut: () -> Unit = {}
    private var onPublishedOut: (Post) -> Unit = {}
    private var onErrorOut: (String) -> Unit = {}

    private var editLoadFailureHandled = false
    private var editorWasReady = false
    private var mediaPickerMode: MediaPickerMode? = null
    private val photoPicker = (lifecycleOwner as androidx.activity.ComponentActivity).activityResultRegistry.register(
        "post-photo-${java.util.UUID.randomUUID()}",
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::selectPhoto) }

    private val composerController = ComposerController(context)

    private var lifecycleObserver: DefaultLifecycleObserver? = null
    private var observerStarted: Boolean = false
    private val observerJobs = mutableListOf<Job>()
    private var screenFullyVisible: Boolean = false

    private var discardHandle: DiscardPostSheet.Handle? = null
    private var flairHandle: FlairPickerSheet.Handle? = null

    private val openFlairRunnable = Runnable {
        showFlairPicker()
    }
    private val openKeyboardRunnable = Runnable {
        if (screenFullyVisible && viewModel.state.value.isEditorReady) composerController.requestKeyboard()
    }

    private val rootView = NativePostEditorView(
        context = context,
        composerController = composerController,
        onPayloadChanged = { text, entities ->
            handleEvent(PostEditorEvent.BodyChanged(text, entities))
        },
    )

    init {
        addView(rootView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        composerController.keyboardAnchor = this
        composerController.changeListener = ::renderUi
        rootView.setEventSink(::handleEvent)
        rootView.setScreenPlaybackGate(EmojiPlaybackGate.Inactive)
        installLifecycleObserver(lifecycleOwner)
        viewModel.begin(mode)
        renderUi()
    }

    fun setCallbacks(
        onBack: () -> Unit,
        onPublished: (Post) -> Unit,
        onError: (String) -> Unit,
    ) {
        onBackOut = onBack
        onPublishedOut = onPublished
        onErrorOut = onError
    }

    private fun startObservers(owner: LifecycleOwner) {
        if (observerStarted) return
        val vm = viewModel
        observerStarted = true

        observerJobs += owner.lifecycleScope.launch {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    handleEditLoadState(vm, state.editPostLoad)
                    state.photoError?.let {
                        onErrorOut(it)
                        vm.consumePhotoError()
                    }
                    when (val submission = state.submission) {
                        is PostEditorState.Success -> {
                            resetComposerSession()
                            vm.consumeSuccess()
                            onPublishedOut(submission.post)
                        }

                        is PostEditorState.Error -> {
                            onErrorOut(submission.message)
                            vm.resetError()
                        }

                        else -> {}
                    }
                    renderUi(state)
                }
            }
        }

        remoteMediaViewModel.prewarmGif()
        remoteMediaViewModel.prewarmMeme()
        observerJobs += owner.lifecycleScope.launch {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                remoteMediaViewModel.state.collect(::onRemoteMediaStateChanged)
            }
        }
    }

    private fun handleEditLoadState(vm: PostEditorViewModel, state: EditPostLoadState) {
        val ready = vm.state.value.isEditorReady
        if (ready && !editorWasReady && screenFullyVisible) {
            removeCallbacks(openKeyboardRunnable)
            post(openKeyboardRunnable)
        }
        editorWasReady = ready
        val edit = mode as? PostEditorMode.Edit ?: return
        if (state is EditPostLoadState.Error && state.postId == edit.postId && !editLoadFailureHandled) {
            editLoadFailureHandled = true
            vm.consumeEditPostLoadError(edit.postId)
            onErrorOut(state.message)
            onBackOut()
        }
    }

    private fun stopObservers() {
        observerJobs.forEach { it.cancel() }
        observerJobs.clear()
        observerStarted = false
    }

    private fun installLifecycleObserver(owner: LifecycleOwner) {
        if (lifecycleObserver != null) return
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (screenFullyVisible) {
                    rootView.onHostStarted()
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                pauseComposerForBackground()
            }
        }
        lifecycleObserver = observer
        owner.lifecycle.addObserver(observer)
    }

    // ── Event dispatch ────────────────────────────────────────────────────────

    private fun handleEvent(event: PostEditorEvent) {
        if (event != PostEditorEvent.RemoteMediaPickerDismissed &&
            (viewModel.state.value.isPreparingPhoto || viewModel.state.value.submission is PostEditorState.Publishing)
        ) return
        when (event) {
            PostEditorEvent.BackTapped -> handleBack()
            PostEditorEvent.PublishTapped -> publishDraft()
            PostEditorEvent.GiphyStickerTapped -> openRemoteMediaPicker(MediaPickerMode.GiphySticker)
            PostEditorEvent.GifTapped -> openRemoteMediaPicker(MediaPickerMode.Gif)
            PostEditorEvent.MemeTapped -> openRemoteMediaPicker(MediaPickerMode.MemeSticker)
            PostEditorEvent.PhotoTapped -> {
                if (!viewModel.state.value.isEditorReady) return
                composerController.dismissAll()
                setMediaPickerMode(null)
                photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                ))
            }
            PostEditorEvent.PollTapped -> viewModel.togglePoll()
            PostEditorEvent.PollOptionAdded -> viewModel.addPollOption()
            is PostEditorEvent.PollOptionRemoved -> viewModel.removePollOption(event.index)
            is PostEditorEvent.PollOptionChanged -> viewModel.setPollOption(event.index, event.text, event.entities)
            PostEditorEvent.FlairTapped -> openFlairPicker()
            PostEditorEvent.EmojiToggleTapped -> {
                if (mediaPickerMode == MediaPickerMode.GiphySticker || mediaPickerMode == MediaPickerMode.Gif ||
                    mediaPickerMode == MediaPickerMode.MemeSticker
                ) {
                    composerController.openEmojiPanel()
                    setMediaPickerMode(null)
                } else {
                    composerController.toggleEmojiPanel()
                }
            }
            is PostEditorEvent.RemoteMediaTapped -> {
                remoteMediaViewModel.rememberSelection(event.media)
                viewModel.selectRemoteMedia(event.media)
                setMediaPickerMode(null)
            }
            is PostEditorEvent.RemoteMediaSearchChanged -> remoteMediaViewModel.search(event.query)
            is PostEditorEvent.RemoteMediaCategorySelected ->
                remoteMediaViewModel.selectCategory(event.categoryId)
            PostEditorEvent.RemoteMediaLoadMore -> remoteMediaViewModel.loadMore()
            PostEditorEvent.RemoteMediaRetry -> remoteMediaViewModel.retry()
            PostEditorEvent.RemoteMediaPickerDismissed -> setMediaPickerMode(null)
            is PostEditorEvent.BodyChanged -> viewModel.setBody(event.text, event.entities)
            PostEditorEvent.MediaRemoved -> viewModel.removeMedia()
        }
    }

    private fun openFlairPicker() {
        composerController.dismissAll()
        setMediaPickerMode(null)
        removeCallbacks(openFlairRunnable)
        postDelayed(openFlairRunnable, FLAIR_SHEET_OPEN_DELAY_MS)
    }

    private fun handleBack() {
        if (mediaPickerMode == MediaPickerMode.GiphySticker || mediaPickerMode == MediaPickerMode.Gif ||
            mediaPickerMode == MediaPickerMode.MemeSticker
        ) {
            setMediaPickerMode(null)
            return
        }
        if (composerController.pickerOpen) {
            composerController.dismissAll()
            return
        }
        composerController.dismissAll()
        if (!viewModel.state.value.hasUnsavedChanges) {
            onBackOut()
        } else {
            showDiscardSheet()
        }
    }

    override fun onBackPressed(): Boolean {
        handleBack()
        return true
    }

    private fun pauseComposerForBackground() {
        composerController.dismissAll()
        setMediaPickerMode(null)
        rootView.onHostStopped()
    }

    override fun onScreenTransitionStart(isOpening: Boolean, isBackward: Boolean) {
        rootView.setScreenPlaybackGate(EmojiPlaybackGate.ActiveFrozen)
    }

    override fun onInteractiveTransitionStart(isOpening: Boolean) {
        // Il gesto di ritorno viene intercettato dall'host di navigazione: chiudi
        // la IME prima di muovere la schermata, altrimenti resta sopra la view
        // precedente durante (e dopo) lo swipe.
        if (!isOpening) composerController.dismissAll()
        rootView.setScreenPlaybackGate(EmojiPlaybackGate.ActiveFrozen)
    }

    override fun onScreenFullyVisible() {
        screenFullyVisible = true
        rootView.setScreenPlaybackGate(EmojiPlaybackGate.FullyVisible)
        rootView.onHostStarted()
        startObservers(lifecycleOwner)
        syncMediaPicker()
        removeCallbacks(openKeyboardRunnable)
        post(openKeyboardRunnable)
    }

    override fun onScreenHidden() {
        screenFullyVisible = false
        removeCallbacks(openKeyboardRunnable)
        stopObservers()
        rootView.setScreenPlaybackGate(EmojiPlaybackGate.Inactive)
        pauseComposerForBackground()
    }

    override fun onScreenDestroyed() {
        photoPicker.unregister()
        viewModel.close()
        screenFullyVisible = false
        stopObservers()
        rootView.setScreenPlaybackGate(EmojiPlaybackGate.Inactive)
        removeCallbacks(openFlairRunnable)
        removeCallbacks(openKeyboardRunnable)
        discardHandle?.dismiss()
        flairHandle?.dismiss()
        discardHandle = null
        flairHandle = null
        rootView.onHostStopped()
        rootView.release()
        lifecycleObserver?.let { observer ->
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
        lifecycleObserver = null
    }

    private fun publishDraft() {
        if (!viewModel.state.value.canPublish) return
        composerController.dismissAll()
        viewModel.submitDraft()
    }

    private fun openRemoteMediaPicker(mode: MediaPickerMode) {
        if (!viewModel.state.value.isEditorReady) return
        val type = when (mode) {
            MediaPickerMode.GiphySticker -> RemoteMediaType.GiphySticker
            MediaPickerMode.Gif -> RemoteMediaType.Gif
            MediaPickerMode.MemeSticker -> RemoteMediaType.Sticker
        }
        if (mediaPickerMode == mode) {
            setMediaPickerMode(null)
            return
        }
        remoteMediaViewModel.open(type)
        composerController.dismissAll()
        setMediaPickerMode(mode)
    }

    private fun showDiscardSheet() {
        if (discardHandle != null) return
        discardHandle = DiscardPostSheet.present(
            anchor = this,
            isEditing = mode is PostEditorMode.Edit,
            onConfirmDiscard = {
                viewModel.clearDraft()
                onBackOut()
            },
            onContinueWriting = {},
            onDismissed = {
                discardHandle = null
            },
        )
    }

    private fun resetComposerSession() {
        composerController.dismissAll()
        removeCallbacks(openFlairRunnable)
        setMediaPickerMode(null)
        viewModel.clearDraft()
    }

    private fun showFlairPicker() {
        if (flairHandle != null) return
        flairHandle = FlairPickerSheet.present(
            anchor = this,
            selected = viewModel.state.value.draft.selectedFlair,
            includeTutti = false,
            onSelected = { flair ->
                viewModel.setFlair(flair)
                flairHandle?.dismiss()
            },
            onDismissed = { flairHandle = null },
        )
    }

    private fun setMediaPickerMode(mode: MediaPickerMode?) {
        if (mediaPickerMode == mode) return
        mediaPickerMode = mode
        syncMediaPicker()
        renderUi()
    }

    private fun syncMediaPicker() {
        val mode = mediaPickerMode
        rootView.setActiveMediaPicker(mode)
        when (mode) {
            null -> {
                rootView.hideRemotePicker()
            }

            MediaPickerMode.GiphySticker,
            MediaPickerMode.Gif,
            MediaPickerMode.MemeSticker,
                -> {
                    rootView.showRemotePicker(mode, remoteMediaViewModel.state.value)
                }
        }
    }

    private fun onRemoteMediaStateChanged(state: RemoteMediaState) {
        if (mediaPickerMode == MediaPickerMode.GiphySticker || mediaPickerMode == MediaPickerMode.Gif ||
            mediaPickerMode == MediaPickerMode.MemeSticker
        ) {
            rootView.bindRemotePicker(state)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(openFlairRunnable)
        removeCallbacks(openKeyboardRunnable)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val FLAIR_SHEET_OPEN_DELAY_MS = 160L
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private fun renderUi() {
        renderUi(viewModel.state.value)
    }

    private var renderedState: PostEditorViewModelState? = null
    private var renderedPickerOpen: Boolean? = null

    private fun renderUi(state: PostEditorViewModelState) {
        val previous = renderedState
        val pickerOpen = composerController.pickerOpen
        val onlyBodyChanged = previous != null && previous.copy(
            draft = previous.draft.copy(bodyText = state.draft.bodyText, bodyEntities = state.draft.bodyEntities),
        ) == state
        if (onlyBodyChanged && previous?.canPublish == state.canPublish &&
            previous?.hasUnsavedChanges == state.hasUnsavedChanges && renderedPickerOpen == pickerOpen
        ) return
        renderedState = state
        renderedPickerOpen = pickerOpen
        val currentUser = state.currentUser
        val uiState = state.uiState(pickerOpen)

        val mediaItem = state.draft.selectedMedia?.toMediaItem()

        rootView.render(
            uiState = uiState,
            seed = currentUser?.id ?: "",
            avatarConfig = currentUser?.avatarConfig,
            nickname = currentUser?.nickname,
            universityName = currentUser?.universityName,
            mediaItem = mediaItem,
        )
    }

}

private fun SelectedPostMedia.toMediaItem(): MediaItem =
    MediaItem(
        url = if (kind == PostMediaKind.Gif) url else previewUrl,
        fallbackUrl = previewFallbackUrl,
        width = width,
        height = height,
    )
