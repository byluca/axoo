package com.axoo.features.posts.presentation.postdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.features.posts.presentation.postdetail.components.trimCommentEmojiPayload
import com.axoo.features.posts.data.PostChange
import com.axoo.features.posts.data.PostRepository
import com.axoo.features.posts.domain.ImageMeta
import com.axoo.features.posts.domain.Comment
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.domain.RetryVoteUseCase
import com.axoo.features.posts.domain.desiredVote
import com.axoo.features.posts.domain.publishErrorMessage
import com.axoo.features.posts.domain.isPermanentClientError
import com.axoo.features.posts.domain.withOptimisticVote
import com.axoo.features.posts.domain.withServerVote
import com.axoo.features.posts.domain.PostAccount
import com.axoo.ui.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


private data class PendingComment(
    val clientId: String,
    val postId: String,
    val body: String,
    val entities: List<MessageEntity>,
    val replyToCommentId: String?,
    val media: ImageMeta? = null,
)

data class CommentsState(
    val post: UiState<Post> = UiState.Loading,
    val comments: UiState<List<CommentUiModel>> = UiState.Loading,
    val currentUserId: String? = null,
    val isRefreshing: Boolean = false,
    val isPullRefreshing: Boolean = false,
    val replyDraft: PostDetailReplyDraftState = PostDetailReplyDraftState(),
    val editingComment: Comment? = null,
    val isSavingCommentEdit: Boolean = false,
    val replyError: String? = null,
    val editFinishedSignal: Int = 0,
)

@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val postRepo: PostRepository,
    private val retryVoteUseCase: RetryVoteUseCase,
    private val account: PostAccount,
    savedState: SavedStateHandle,
) : ViewModel() {

    val postId: String = checkNotNull(savedState["postId"])

    private val _state = MutableStateFlow(CommentsState())
    val state: StateFlow<CommentsState> = _state.asStateFlow()

    private val pendingJobs = mutableMapOf<String, Job>()
    private val commentVoteJobs = mutableMapOf<String, Job>()
    private var postVoteJob: Job? = null
    private var draftBeforeEditing: PostDetailReplyDraftState? = null

    enum class ReplySubmission { Sent, EditSaving, EditFinished }

    fun allows(event: PostDetailEvent): Boolean {
        if (_state.value.editingComment == null) return true
        return event == PostDetailEvent.BackTapped || event == PostDetailEvent.SendReplyTapped ||
            event is PostDetailEvent.ReplyDraftChanged || event is PostDetailEvent.ReplySelectionChanged
    }

    fun updateReplyDraft(text: String, entities: List<MessageEntity>, selectionStart: Int, selectionEnd: Int) {
        if (_state.value.isSavingCommentEdit) return
        _state.update { it.copy(replyDraft = it.replyDraft.copy(
            text = text, entities = entities.toList(), selectionStart = selectionStart, selectionEnd = selectionEnd,
        ), replyError = null) }
    }

    fun updateReplySelection(start: Int, end: Int) {
        _state.update { it.copy(replyDraft = it.replyDraft.copy(selectionStart = start, selectionEnd = end)) }
    }

    fun beginReply(comment: Comment): Boolean {
        if (comment.isDeleted || comment.id.startsWith("local_") || _state.value.editingComment != null) return false
        _state.update { it.copy(replyDraft = it.replyDraft.copy(replyToCommentId = comment.id)) }
        return true
    }

    fun cancelReply() {
        _state.update { it.copy(replyDraft = it.replyDraft.copy(replyToCommentId = null)) }
    }

    fun beginEdit(comment: Comment): Boolean {
        val state = _state.value
        if (state.isSavingCommentEdit || !comment.canEdit ||
            comment.userId != state.currentUserId) return false
        if (state.editingComment == null) draftBeforeEditing = state.replyDraft
        _state.update { it.copy(editingComment = comment, replyError = null,
            replyDraft = PostDetailReplyDraftState(text = comment.body, entities = comment.entities)) }
        return true
    }

    fun cancelEdit(): Boolean {
        if (_state.value.isSavingCommentEdit || _state.value.editingComment == null) return false
        finishEdit()
        return true
    }

    private fun finishEdit() {
        val previous = draftBeforeEditing ?: PostDetailReplyDraftState()
        draftBeforeEditing = null
        _state.update { it.copy(editingComment = null, isSavingCommentEdit = false, replyDraft = previous,
            editFinishedSignal = it.editFinishedSignal + 1) }
    }

    fun clearReplyError() { _state.update { it.copy(replyError = null) } }

    fun submitReply(): ReplySubmission? {
        val state = _state.value
        if (state.replyDraft.isBlank || state.isSavingCommentEdit) return null
        val payload = trimCommentEmojiPayload(state.replyDraft.text, state.replyDraft.entities)
        val entities = payload.entities.distinctBy { Triple(it.type, it.offset, it.userId ?: it.customEmojiId.toString()) }
            .sortedBy(MessageEntity::offset)
        val edited = state.editingComment
        if (edited == null) {
            _state.update { it.copy(replyDraft = PostDetailReplyDraftState()) }
            createComment(payload.text, entities, state.replyDraft.replyToCommentId)
            return ReplySubmission.Sent
        }
        if (payload.text == edited.body && entities == edited.entities) {
            finishEdit()
            return ReplySubmission.EditFinished
        }
        _state.update { it.copy(isSavingCommentEdit = true, replyError = null) }
        editComment(edited.id, payload.text, entities) { result ->
            if (_state.value.editingComment?.id != edited.id) return@editComment
            result.onSuccess { finishEdit() }.onFailure {
                _state.update { it.copy(isSavingCommentEdit = false, replyError = (result.exceptionOrNull() as? Exception)?.publishErrorMessage()
                        ?.takeIf { it == "Il testo contiene parole non consentite. Modificalo e riprova." } ?: "Modifica non riuscita. Riprova.") }
            }
        }
        return ReplySubmission.EditSaving
    }

    init {
        viewModelScope.launch {
            postRepo.changes.collect { change ->
                if (change is PostChange.PollChanged && change.id == postId) updatePost { it.copy(poll = change.poll) }
            }
        }
        loadPost()
        loadCurrentUserId()
        viewModelScope.launch { loadCommentsNow() }
    }

    private fun loadCurrentUserId() {
        val cachedUserId = account.cachedAuthor()?.id
        _state.update { it.copy(currentUserId = cachedUserId) }
        if (cachedUserId != null) return
        viewModelScope.launch {
            val currentUserId = runCatching { account.loadAuthor() }.getOrNull()?.id
            _state.update { it.copy(currentUserId = currentUserId) }
        }
    }

    fun isModerator(): Boolean = account.cachedAuthor()?.isModerator == true

    fun loadPost() {
        viewModelScope.launch {
            if (_state.value.post !is UiState.Success) {
                _state.update { it.copy(post = UiState.Loading) }
            }
            val post = runCatching { postRepo.getPost(postId) }
                .fold({ UiState.Success(it) }, { UiState.Error(it) })
            _state.update { it.copy(post = post) }
        }
    }

    fun refreshPost() {
        viewModelScope.launch {
            val current = _state.value.post
            val post = runCatching { postRepo.getPost(postId) }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { if (current is UiState.Success) current else UiState.Error(it) },
                )
            _state.update { it.copy(post = post) }
        }
    }

    fun refreshPostIfLoaded() {
        if (!_state.value.isRefreshing && _state.value.post is UiState.Success) refreshPost()
    }

    fun refreshDetail(showIndicator: Boolean = true) {
        if (_state.value.isRefreshing) {
            if (showIndicator) _state.update { it.copy(isPullRefreshing = true) }
            return
        }
        _state.update { it.copy(isRefreshing = true, isPullRefreshing = showIndicator) }
        viewModelScope.launch {
            val currentPost = _state.value.post
            val currentComments = _state.value.comments
            try {
                val postRequest = async { runCatching { postRepo.getPost(postId) } }
                val commentsRequest = async {
                    runCatching { postRepo.getComments(postId) }
                }
                val postResult = postRequest.await()
                val commentsResult = commentsRequest.await()
                _state.update { latest ->
                    latest.copy(
                        post = postResult.fold(
                            onSuccess = { UiState.Success(it) },
                            onFailure = {
                                if (currentPost is UiState.Success) currentPost else UiState.Error(it)
                            },
                        ),
                        comments = commentsResult.fold(
                            onSuccess = { fetched -> UiState.Success(mergePendingInto(fetched.toUiModels())) },
                            onFailure = {
                                if (currentComments is UiState.Success) currentComments else UiState.Error(it)
                            },
                        ),
                    )
                }
            } finally {
                _state.update { it.copy(isRefreshing = false, isPullRefreshing = false) }
            }
        }
    }

    fun votePoll(optionId: String, onFailure: () -> Unit) {
        val poll = (_state.value.post as? UiState.Success)?.data?.poll ?: return
        if (poll.hasVoted || poll.isSending) return
        viewModelScope.launch {
            try { postRepo.votePoll(postId, poll, optionId) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { onFailure() }
        }
    }

    fun votePost(type: String) {
        val current = (_state.value.post as? UiState.Success)?.data ?: return
        val next = desiredVote(current.myVote, type)
        updatePost { current.withOptimisticVote(next) }

        postVoteJob?.cancel()
        postVoteJob = viewModelScope.launch {
            val result = retryVoteUseCase { postRepo.votePost(postId, next.toInt) }
            if (result != null) {
                val latest = (_state.value.post as? UiState.Success)?.data ?: return@launch
                if (latest.myVote == next) updatePost { latest.withServerVote(result) }
            } else {
                val latest = (_state.value.post as? UiState.Success)?.data ?: return@launch
                if (latest.myVote == next) updatePost { it.copy(
                    upvotes = current.upvotes, downvotes = current.downvotes, myVoteRaw = current.myVoteRaw,
                ) }
            }
        }
    }

    fun loadComments() {
        viewModelScope.launch { loadCommentsNow() }
    }

    private suspend fun loadCommentsNow() {
        if (_state.value.comments !is UiState.Success) {
            _state.update { it.copy(comments = UiState.Loading) }
        }
        val comments = runCatching { postRepo.getComments(postId) }
            .fold(
                onSuccess = { fetched -> UiState.Success(mergePendingInto(fetched.toUiModels())) },
                onFailure = { error ->
                    if (_state.value.comments is UiState.Success) _state.value.comments else UiState.Error(error)
                },
            )
        _state.update { it.copy(comments = comments) }
    }


    fun sendMedia(media: ImageMeta) {
        if (_state.value.editingComment != null) return
        createComment("", replyToCommentId = _state.value.replyDraft.replyToCommentId, media = media)
        cancelReply()
    }

    fun createComment(
        body: String,
        entities: List<MessageEntity> = emptyList(),
        replyToCommentId: String? = null,
        media: ImageMeta? = null,
    ) {
        val clientId = UUID.randomUUID().toString()
        val pending = PendingComment(
            clientId = clientId,
            postId = postId,
            body = body,
            entities = entities,
            replyToCommentId = replyToCommentId,
            media = media,
        )
        addOptimistic(pending)
        sendPending(pending)
    }

    private fun sendPending(pending: PendingComment) {
        pendingJobs.remove(pending.clientId)?.cancel()
        pendingJobs[pending.clientId] = viewModelScope.launch {
            var attempt = 0
            while (isActive) {
                val result = runCatching {
                    postRepo.createComment(
                        postId = pending.postId,
                        clientId = pending.clientId,
                        body = pending.body,
                        entities = pending.entities,
                        replyToCommentId = pending.replyToCommentId,
                        media = pending.media,
                    )
                }
                result.onSuccess { real ->
                    replaceOptimistic(pending.clientId, real.toUiModel())
                    pendingJobs.remove(pending.clientId)
                    return@launch
                }
                val error = result.exceptionOrNull()
                if (error is CancellationException) throw error
                if (isPermanentClientError(error)) {
                    val message = (error as? Exception)?.publishErrorMessage()
                    if (message == "Il testo contiene parole non consentite. Modificalo e riprova.") {
                        _state.update { state ->
                            val draft = if (state.replyDraft.isBlank && state.replyDraft.replyToCommentId == null && state.editingComment == null) {
                                PostDetailReplyDraftState(text = pending.body, entities = pending.entities,
                                    selectionStart = pending.body.length, selectionEnd = pending.body.length,
                                    replyToCommentId = pending.replyToCommentId)
                            } else state.replyDraft
                            state.copy(replyDraft = draft, replyError = message)
                        }
                    }
                    if (pending.media != null) _state.update { it.copy(replyError = "Invio non riuscito. Riprova.") }
                    removeByClientId(pending.clientId)
                    pendingJobs.remove(pending.clientId)
                    return@launch
                }
                delay(RetryVoteUseCase.RETRY_DELAYS_MS[attempt.coerceAtMost(RetryVoteUseCase.RETRY_DELAYS_MS.lastIndex)])
                attempt++
            }
        }
    }

    fun deleteComment(commentId: String) {
        val current = currentComments()
        if (current.isEmpty()) return
        setComments(withDeletedComment(current, commentId))
        viewModelScope.launch {
            runCatching { postRepo.deleteComment(postId, commentId) }
                .onFailure { setComments(current) }
        }
    }

    fun editComment(
        commentId: String,
        body: String,
        entities: List<MessageEntity>,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val target = currentComments().firstOrNull { it.id == commentId }
        if (target == null || target.deliveryState != CommentDeliveryState.Sent || !target.comment.canEdit) {
            onResult(Result.failure(IllegalStateException("Commento non disponibile.")))
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                postRepo.updateComment(postId, commentId, body, entities)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            result.onSuccess {
                updateComment(commentId) { item ->
                    item.copy(
                        comment = item.comment.copy(
                            body = body,
                            entities = entities,
                            updatedAt = nowIso8601(),
                        ),
                    )
                }
            }
            onResult(result)
            if (result.isSuccess) loadCommentsNow()
        }
    }

    fun deletePost(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { postRepo.deletePost(postId) })
        }
    }

    fun report(
        targetType: String,
        targetId: String,
        reason: String,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { postRepo.report(targetType, targetId, reason) }
                .onSuccess { onSuccess() }
                .onFailure(onFailure)
        }
    }

    fun hidePost(postId: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = try { postRepo.hidePost(postId); Result.success(Unit) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { Result.failure(error) }
            onResult(result)
        }
    }

    fun blockUser(userId: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { account.blockUser(userId) }
            if (result.isSuccess) {
                val current = currentComments()
                val blockedIds = current.filter { it.comment.userId == userId }.map { it.id }
                setComments(blockedIds.fold(current) { rows, id -> withDeletedComment(rows, id) })
            }
            onResult(result)
        }
    }

    fun voteComment(commentId: String, type: String) {
        val current = (_state.value.comments as? UiState.Success)?.data ?: return
        val target = current.firstOrNull { it.id == commentId } ?: return
        if (target.deliveryState != CommentDeliveryState.Sent || target.comment.isDeleted) return
        val next = desiredVote(target.comment.myVote, type)
        updateComment(commentId) { it.copy(comment = it.comment.withOptimisticVote(next)) }

        commentVoteJobs.remove(commentId)?.cancel()
        commentVoteJobs[commentId] = viewModelScope.launch {
            val runningJob = coroutineContext[Job]
            try {
                val result = retryVoteUseCase { postRepo.voteComment(postId, commentId, next.toInt) }
                ensureActive()
                val latest = currentComments().firstOrNull { it.id == commentId } ?: return@launch
                if (latest.comment.myVote != next) return@launch
                if (result != null) {
                    updateComment(commentId) { it.copy(comment = it.comment.withServerVote(result)) }
                } else {
                    updateComment(commentId) { target }
                }
            } finally {
                if (commentVoteJobs[commentId] === runningJob) commentVoteJobs.remove(commentId)
            }
        }
    }

    private fun List<Comment>.toUiModels(): List<CommentUiModel> = map { it.toUiModel() }

    private fun Comment.toUiModel() = CommentUiModel(
        comment = this,
        deliveryState = CommentDeliveryState.Sent,
        localId = null,
    )

    private fun mergePendingInto(fetched: List<CommentUiModel>): List<CommentUiModel> {
        val serverClientIds = fetched.mapNotNullTo(hashSetOf()) { it.comment.clientId }
        val pending = currentComments().filter { it.isOptimistic && it.comment.clientId !in serverClientIds }
        return fetched + pending
    }

    private fun addOptimistic(pending: PendingComment) {
        if (currentComments().any { it.comment.clientId == pending.clientId }) return
        val me = account.cachedAuthor()
        val comment = Comment(
            id = "local_${pending.clientId}",
            postId = pending.postId,
            clientId = pending.clientId,
            parentCommentId = pending.replyToCommentId,
            userId = me?.id.orEmpty(),
            nickname = me?.nickname ?: "Tu",
            body = pending.body,
            media = pending.media,
            entities = pending.entities,
            createdAt = nowIso8601(),
            avatarConfig = me?.avatarConfig,
        )
        setComments(
            currentComments() + CommentUiModel(
                comment,
                CommentDeliveryState.Sending,
                pending.clientId,
            ),
        )
    }

    private fun replaceOptimistic(clientId: String, new: CommentUiModel) {
        setComments(currentComments().map { if (it.comment.clientId == clientId) new else it })
    }

    private fun removeByClientId(clientId: String) {
        setComments(currentComments().filterNot { it.comment.clientId == clientId })
    }

    private fun updateComment(id: String, transform: (CommentUiModel) -> CommentUiModel) {
        setComments(currentComments().map { if (it.id == id) transform(it) else it })
    }

    private fun currentComments(): List<CommentUiModel> =
        (_state.value.comments as? UiState.Success)?.data.orEmpty()

    private fun setComments(comments: List<CommentUiModel>) {
        _state.update { it.copy(comments = UiState.Success(comments)) }
    }

    private fun updatePost(transform: (Post) -> Post) {
        _state.update { state ->
            val current = (state.post as? UiState.Success)?.data ?: return@update state
            state.copy(post = UiState.Success(transform(current)))
        }
    }

    private fun nowIso8601(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

}
