package com.axoo.features.posts.presentation.posteditor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.features.posts.data.PostRepository
import com.axoo.features.posts.data.PostPhotoRepository
import com.axoo.features.posts.domain.ImageMeta
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.domain.RemoteMedia
import com.axoo.features.posts.domain.RemoteMediaType
import com.axoo.features.posts.domain.PollOptionInput
import com.axoo.features.posts.domain.MIN_POLL_OPTIONS
import com.axoo.features.posts.domain.MAX_POLL_OPTIONS
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.axoo.features.posts.domain.publishErrorMessage
import com.axoo.features.posts.domain.PostAccount
import com.axoo.features.posts.domain.PostAuthor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class PostEditorState {
    data object Idle : PostEditorState()
    data object Publishing : PostEditorState()
    data class Success(val post: Post) : PostEditorState()
    data class Error(val message: String) : PostEditorState()
}

sealed interface EditPostLoadState {
    data object Idle : EditPostLoadState
    data class Loading(val postId: String) : EditPostLoadState
    data class Loaded(val post: Post) : EditPostLoadState
    data class Error(val postId: String, val message: String) : EditPostLoadState
}

data class PostEditorViewModelState(
    val submission: PostEditorState = PostEditorState.Idle,
    val currentUser: PostAuthor? = null,
    val editPostLoad: EditPostLoadState = EditPostLoadState.Idle,
    val draft: PostEditorDraftState = PostEditorDraftState(),
    val initialDraft: PostEditorDraftState? = null,
    val isEditing: Boolean = false,
    val isPreparingPhoto: Boolean = false,
    val photoError: String? = null,
) {
    val isEditorReady: Boolean
        get() = !isPreparingPhoto && (!isEditing || initialDraft != null)
    val hasUnsavedChanges: Boolean
        get() = hasPostEditorUnsavedChanges(isEditing, initialDraft, draft)
    val canPublish: Boolean get() = uiState().canPublish

    fun uiState(pickerOpen: Boolean = false) = PostEditorUiState(
        draft = draft,
        isPublishing = submission is PostEditorState.Publishing,
        hasCurrentUser = currentUser != null,
        pickerOpen = pickerOpen,
        isEditing = isEditing,
        hasUnsavedChanges = hasUnsavedChanges,
        isEditorReady = isEditorReady,
    )
}

@HiltViewModel
class PostEditorViewModel @Inject constructor(
    private val postRepo: PostRepository,
    private val account: PostAccount,
    private val photoRepo: PostPhotoRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PostEditorViewModelState())
    val state: StateFlow<PostEditorViewModelState> = _state.asStateFlow()

    private var pendingPublish: PendingPublish? = null
    private val uploadedPhotos = mutableMapOf<String, ImageMeta>()

    private var mode: PostEditorMode = PostEditorMode.Create
    private var generation = UUID.randomUUID()
    private var photoJob: Job? = null
    private var editLoadJob: Job? = null
    private var submissionJob: Job? = null

    internal fun begin(mode: PostEditorMode) {
        close()
        this.mode = mode
        val initial = (mode as? PostEditorMode.Edit)?.initialPost
            ?.takeIf { it.id == mode.postId && it.poll == null }?.toEditorDraft()
        _state.update { it.copy(
            currentUser = account.cachedAuthor() ?: it.currentUser,
            draft = initial ?: PostEditorDraftState(),
            initialDraft = initial,
            isEditing = mode is PostEditorMode.Edit,
        ) }
        if (mode is PostEditorMode.Edit && initial == null) loadPostForEditing(mode.postId)
    }

    fun setBody(text: String, entities: List<MessageEntity>) {
        if (!canChangeDraft()) return
        _state.update { it.copy(draft = it.draft.copy(bodyText = text, bodyEntities = entities.toList())) }
    }

    fun togglePoll() {
        if (!canChangeDraft() || _state.value.isEditing) return
        _state.update { it.copy(draft = it.draft.copy(pollOptions =
            if (it.draft.pollOptions == null) List(MIN_POLL_OPTIONS) { PollOptionInput() } else null)) }
    }

    fun addPollOption() {
        if (!canChangeDraft()) return
        _state.update { state ->
            val options = state.draft.pollOptions ?: return@update state
            if (options.size >= MAX_POLL_OPTIONS) state
            else state.copy(draft = state.draft.copy(pollOptions = options + PollOptionInput()))
        }
    }

    fun removePollOption(index: Int) {
        if (!canChangeDraft()) return
        _state.update { state ->
            val options = state.draft.pollOptions ?: return@update state
            if (options.size <= MIN_POLL_OPTIONS || index !in options.indices) state
            else state.copy(draft = state.draft.copy(pollOptions = options.filterIndexed { i, _ -> i != index }))
        }
    }

    fun setPollOption(index: Int, text: String, entities: List<MessageEntity>) {
        if (!canChangeDraft()) return
        _state.update { state -> state.copy(draft = state.draft.copy(pollOptions =
            state.draft.pollOptions?.mapIndexed { i, option ->
                if (i == index) PollOptionInput(text, entities.toList()) else option
            })) }
    }

    fun setFlair(flair: String?) {
        if (!canChangeDraft()) return
        _state.update { it.copy(draft = it.draft.copy(selectedFlair = flair)) }
    }

    fun selectRemoteMedia(media: RemoteMedia) {
        if (!canChangeDraft()) return
        val kind = when (media.type) {
            RemoteMediaType.Gif -> PostMediaKind.Gif
            RemoteMediaType.Sticker -> PostMediaKind.Sticker
            RemoteMediaType.Meme -> PostMediaKind.Meme
        }
        setMedia(SelectedPostMedia(
            kind = kind,
            url = media.mediaUrl,
            previewUrl = if (kind == PostMediaKind.Gif) media.loadingPreviewUrl ?: media.mediaUrl else media.previewUrl,
            previewFallbackUrl = media.previewFallbackUrl,
            width = media.width,
            height = media.height,
            mimeType = media.mimeType ?: if (kind != PostMediaKind.Meme) "image/gif" else media.mediaUrl.imageMimeType(),
            provider = media.provider,
            providerId = media.id,
        ))
    }

    fun removeMedia() {
        if (canChangeDraft()) setMedia(null)
    }

    private fun setMedia(media: SelectedPostMedia?) {
        discardPhoto(_state.value.draft.selectedMedia)
        _state.update { it.copy(draft = it.draft.copy(selectedMedia = media)) }
    }

    private fun canChangeDraft(): Boolean = _state.value.isEditorReady &&
        _state.value.submission !is PostEditorState.Publishing

    fun selectPhoto(uri: Uri) {
        if (!canChangeDraft()) return
        val token = generation
        _state.update { it.copy(isPreparingPhoto = true, photoError = null) }
        photoJob = viewModelScope.launch {
            try {
                val photo = photoRepo.prepare(uri)
                if (generation != token || currentCoroutineContext()[Job]?.isActive == false) {
                    photoRepo.discard(photo)
                    return@launch
                }
                setMedia(photo)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == token) _state.update {
                    it.copy(photoError = error.message ?: "Impossibile aprire la foto")
                }
            } finally {
                if (generation == token) {
                    photoJob = null
                    _state.update { it.copy(isPreparingPhoto = false) }
                }
            }
        }
    }

    fun consumePhotoError() { _state.update { it.copy(photoError = null) } }

    fun submitDraft() {
        val state = _state.value
        if (!state.canPublish) return
        val payload = trimPostEditorEmojiPayload(state.draft.bodyText, state.draft.bodyEntities)
        when (val currentMode = mode) {
            PostEditorMode.Create -> publish(payload.text, payload.entities, state.draft.selectedFlair, state.draft.selectedMedia,
                state.draft.pollOptions?.map { option ->
                    val trimmed = trimPostEditorEmojiPayload(option.text, option.entities)
                    PollOptionInput(trimmed.text, trimmed.entities)
                })
            is PostEditorMode.Edit -> update(currentMode.postId, payload.text, payload.entities, state.draft.selectedFlair, state.draft.selectedMedia)
        }
    }

    fun clearDraft() {
        if (_state.value.submission is PostEditorState.Publishing) return
        abandonPendingPublish()
        discardPhoto(_state.value.draft.selectedMedia)
        _state.update { it.copy(draft = PostEditorDraftState()) }
    }

    fun close() {
        generation = UUID.randomUUID()
        photoJob?.cancel()
        editLoadJob?.cancel()
        submissionJob?.cancel()
        photoJob = null
        editLoadJob = null
        submissionJob = null
        photoRepo.discard(_state.value.draft.selectedMedia)
        uploadedPhotos.clear()
        pendingPublish = null
        _state.update { PostEditorViewModelState(currentUser = it.currentUser) }
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }

    private fun discardPhoto(media: SelectedPostMedia?) {
        if (_state.value.submission is PostEditorState.Publishing) return
        media?.let { uploadedPhotos.remove(it.url) }
        photoRepo.discard(media)
    }

    private suspend fun resolveMedia(media: SelectedPostMedia?): ImageMeta? {
        if (media?.kind != PostMediaKind.Photo || !media.url.startsWith("file:")) return media.toMediaMetadata()
        // Reuse the upload on retries, including an uncertain publish response.
        return uploadedPhotos[media.url] ?: photoRepo.upload(media).also {
            currentCoroutineContext().ensureActive()
            uploadedPhotos[media.url] = it
        }
    }

    init {
        viewModelScope.launch {
            account.cachedAuthor()?.let { cachedUser ->
                _state.update { it.copy(currentUser = cachedUser) }
            }
            runCatching { account.loadAuthor() }.getOrNull()?.let { currentUser ->
                _state.update { it.copy(currentUser = currentUser) }
            }
        }
    }

    fun publish(
        body: String,
        entities: List<MessageEntity> = emptyList(),
        flair: String? = null,
        selectedMedia: SelectedPostMedia? = null,
        pollOptions: List<PollOptionInput>? = null,
    ) {
        if (_state.value.currentUser == null) return
        if (_state.value.submission is PostEditorState.Publishing) return
        val content = PendingPublishContent(
            body = body,
            entities = entities.toList(),
            flair = flair,
            selectedMedia = selectedMedia,
            pollOptions = pollOptions,
        )
        val clientId = pendingPublish
            ?.takeIf { it.content == content }
            ?.clientId
            ?: UUID.randomUUID().toString()
        pendingPublish = PendingPublish(clientId, content)
        setSubmission(PostEditorState.Publishing)
        val token = generation
        submissionJob = viewModelScope.launch {
            try {
                val post = postRepo.postEditor(
                    clientId = clientId,
                    body = body,
                    entities = entities,
                    flair = flair,
                    media = resolveMedia(selectedMedia),
                    pollOptions = pollOptions,
                )
                currentCoroutineContext().ensureActive()
                if (generation != token) return@launch
                pendingPublish = null
                photoRepo.discard(selectedMedia)
                uploadedPhotos.clear()
                setSubmission(PostEditorState.Success(post))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (generation == token) setSubmission(PostEditorState.Error(e.publishErrorMessage()))
            }
        }
    }

    fun resetError() {
        if (_state.value.submission is PostEditorState.Error) setSubmission(PostEditorState.Idle)
    }

    fun consumeSuccess() {
        if (_state.value.submission is PostEditorState.Success) setSubmission(PostEditorState.Idle)
    }

    private fun abandonPendingPublish() {
        if (_state.value.submission !is PostEditorState.Publishing) pendingPublish = null
    }

    fun loadPostForEditing(postId: String) {
        when (val current = _state.value.editPostLoad) {
            is EditPostLoadState.Loading -> if (current.postId == postId) return
            is EditPostLoadState.Loaded -> if (current.post.id == postId) return
            else -> Unit
        }
        editLoadJob?.cancel()
        val token = generation
        setEditPostLoad(EditPostLoadState.Loading(postId))
        editLoadJob = viewModelScope.launch {
            try {
                val post = postRepo.getPost(postId)
                check(post.poll == null) { "I post con sondaggio non possono essere modificati." }
                currentCoroutineContext().ensureActive()
                if (generation != token) return@launch
                val loading = _state.value.editPostLoad as? EditPostLoadState.Loading
                if (loading?.postId != postId) return@launch
                val edit = mode as? PostEditorMode.Edit
                if (edit?.postId == postId && _state.value.initialDraft == null) {
                    val draft = post.toEditorDraft()
                    _state.update { it.copy(draft = draft, initialDraft = draft, editPostLoad = EditPostLoadState.Loaded(post)) }
                } else setEditPostLoad(EditPostLoadState.Loaded(post))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == token) setEditPostLoad(EditPostLoadState.Error(postId, error.message ?: "Post non disponibile"))
            }
        }
    }

    fun consumeEditPostLoadError(postId: String) {
        val error = _state.value.editPostLoad as? EditPostLoadState.Error ?: return
        if (error.postId == postId) setEditPostLoad(EditPostLoadState.Idle)
    }

    fun update(
        postId: String,
        body: String,
        entities: List<MessageEntity> = emptyList(),
        flair: String? = null,
        selectedMedia: SelectedPostMedia? = null,
    ) {
        if (_state.value.submission is PostEditorState.Publishing) return
        setSubmission(PostEditorState.Publishing)
        val token = generation
        submissionJob = viewModelScope.launch {
            try {
                val post = postRepo.updatePost(
                    id = postId,
                    body = body,
                    entities = entities,
                    flair = flair,
                    media = resolveMedia(selectedMedia),
                )
                currentCoroutineContext().ensureActive()
                if (generation != token) return@launch
                photoRepo.discard(selectedMedia)
                uploadedPhotos.clear()
                setSubmission(PostEditorState.Success(post))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (generation == token) setSubmission(PostEditorState.Error(e.publishErrorMessage()))
            }
        }
    }

    private fun setSubmission(submission: PostEditorState) {
        _state.update { it.copy(submission = submission) }
    }

    private fun setEditPostLoad(editPostLoad: EditPostLoadState) {
        _state.update { it.copy(editPostLoad = editPostLoad) }
    }
}

private fun SelectedPostMedia?.toMediaMetadata(): ImageMeta? =
    this?.let { media ->
        ImageMeta(
            url = media.url,
            thumbnailUrl = media.previewUrl,
            width = media.width,
            height = media.height,
            type = media.kind.apiValue,
            mimeType = media.mimeType,
            previewFallbackUrl = media.previewFallbackUrl,
            provider = media.provider,
            providerId = media.providerId,
        )
    }

private data class PendingPublish(
    val clientId: String,
    val content: PendingPublishContent,
)

private data class PendingPublishContent(
    val body: String,
    val entities: List<MessageEntity>,
    val flair: String?,
    val selectedMedia: SelectedPostMedia?,
    val pollOptions: List<PollOptionInput>?,
)

private fun Post.toEditorDraft(): PostEditorDraftState {
    val image = media
    val kind = when (image?.type) {
        "sticker" -> PostMediaKind.Sticker
        "gif" -> PostMediaKind.Gif
        "photo" -> PostMediaKind.Photo
        else -> PostMediaKind.Meme
    }
    return PostEditorDraftState(
        bodyText = body,
        bodyEntities = entities,
        selectedFlair = flair.ifBlank { null },
        selectedMedia = image?.let {
            SelectedPostMedia(
                kind = kind,
                url = it.url,
                previewUrl = it.thumbnailUrl ?: it.url,
                previewFallbackUrl = it.previewFallbackUrl,
                width = it.width,
                height = it.height,
                mimeType = it.mimeType,
                provider = it.provider,
                providerId = it.providerId,
            )
        },
    )
}

private fun String.imageMimeType(): String = when (
    substringBefore('?').substringAfterLast('.', "").lowercase()
) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    else -> "image/jpeg"
}
