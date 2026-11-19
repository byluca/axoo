package com.axoo.features.posts.data

import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.engine.emoji.text.toApiEntityOffsets
import com.axoo.engine.emoji.text.toUiEntityOffsets
import com.axoo.features.posts.data.dto.CreateCommentRequest
import com.axoo.features.posts.data.dto.CreatePostRequest
import com.axoo.features.posts.data.dto.NewFeedSeenRequest
import com.axoo.features.moderation.data.ReportRequest
import com.axoo.features.moderation.data.ReportsApi
import com.axoo.features.posts.data.dto.UpdateCommentRequest
import com.axoo.features.posts.data.dto.UpdatePostRequest
import com.axoo.features.posts.data.dto.toDomain
import com.axoo.features.posts.domain.VoteResult
import com.axoo.features.posts.data.dto.VoteRequest
import com.axoo.features.posts.domain.Comment
import com.axoo.features.posts.domain.ImageMeta
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.domain.PostPoll
import com.axoo.features.posts.domain.PollOptionInput
import com.axoo.features.posts.data.dto.CreatePollRequest
import com.axoo.features.posts.data.dto.PollVoteRequest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PostsPage(val posts: List<Post>, val nextCursor: String?)

sealed interface PostChange {
    data object Reset : PostChange
    data class Updated(val post: Post) : PostChange
    data class Created(val post: Post) : PostChange
    data class Deleted(val id: String) : PostChange
    data class Voted(val id: String, val vote: VoteResult) : PostChange
    data class PollChanged(val id: String, val poll: PostPoll) : PostChange
}

@Singleton
class PostRepository @Inject constructor(
    private val reportsApi: ReportsApi,
    private val api: PostsApi,
    private val klipy: KlipyRepository,
) {
    private val _changes = MutableSharedFlow<PostChange>(extraBufferCapacity = 64)
    val changes = _changes.asSharedFlow()

    suspend fun getMyPostsPage(cursor: String? = null, limit: Int = 20, authorId: String? = null): PostsPage {
        val params = buildMap {
            put("limit", limit.toString())
            cursor?.let { put("cursor", it) }
        }
        val response = if (authorId == null) api.getMyPosts(params) else api.getUserPosts(authorId, params)
        return PostsPage(response.posts.filterNot { it.userId in _blockedAuthorIds.value || it.id in _hiddenPostIds.value }.map { it.withUiEntityOffsets() }, response.nextCursor?.takeIf { it.isNotBlank() })
    }

    private val _blockedAuthorIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedAuthorIds = _blockedAuthorIds.asStateFlow()
    private var restrictionsGeneration = 0L
    private val _hiddenPostIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenPostIds = _hiddenPostIds.asStateFlow()
    private val postVoteLocks = ConcurrentHashMap<String, Mutex>()
    private val commentVoteLocks = ConcurrentHashMap<String, Mutex>()
    private val pollVoteLocks = ConcurrentHashMap<String, Mutex>()
    private val pollSnapshots = ConcurrentHashMap<String, PostPoll>()

    // ── Posts ──────────────────────────────────────────────────────────────────

    suspend fun getPostsPage(
        order: String = "hot",
        cursor: String? = null,
        limit: Int = 20,
    ): PostsPage {
        val params = buildMap<String, String> {
            put("order", order)
            put("limit", limit.toString())
            cursor?.let { put("cursor", it) }
        }
        val response = api.getPosts(params)
        val rawCursor = response.nextCursor?.takeIf { it.isNotEmpty() }
        return PostsPage(response.posts.filterNot { it.userId in _blockedAuthorIds.value || it.id in _hiddenPostIds.value }.map { it.withUiEntityOffsets() }, rawCursor)
    }

    suspend fun getPost(id: String): Post {
        val post = api.getPost(id).post.withUiEntityOffsets()
        check(post.userId !in _blockedAuthorIds.value) { "Contenuto non disponibile" }
        _changes.emit(PostChange.Updated(post))
        return post
    }

    suspend fun getNewFeedUnreadCount(): Int =
        api.getNewFeedUnreadCount().unreadCount.coerceAtLeast(0)

    suspend fun markNewFeedSeen(seenThrough: String) {
        api.markNewFeedSeen(NewFeedSeenRequest(seenThrough))
    }

    suspend fun postEditor(
        clientId: String,
        body: String,
        entities: List<MessageEntity> = emptyList(),
        flair: String? = null,
        media: ImageMeta? = null,
        pollOptions: List<PollOptionInput>? = null,
    ): Post {
        val response = api.postEditor(
            CreatePostRequest(
                body = body,
                entities = entities.toApiEntityOffsets(body),
                flair = flair,
                media = media,
                clientId = clientId,
                poll = pollOptions?.let { options -> CreatePollRequest(options.map {
                    it.copy(entities = it.entities.toApiEntityOffsets(it.text))
                }) },
            ),
        )
        klipy.reportShare(media)
        return response.post.withUiEntityOffsets().also { _changes.emit(PostChange.Created(it)) }
    }

    suspend fun updatePost(
        id: String,
        body: String,
        entities: List<MessageEntity> = emptyList(),
        flair: String? = null,
        media: ImageMeta? = null,
    ): Post {
        api.updatePost(id, UpdatePostRequest(body, entities.toApiEntityOffsets(body), flair, media))
        val response = api.getPost(id)
        klipy.reportShare(media)
        return response.post.withUiEntityOffsets().also { _changes.emit(PostChange.Updated(it)) }
    }

    suspend fun deletePost(id: String) {
        api.deletePost(id)
        _changes.emit(PostChange.Deleted(id))
    }

    suspend fun setAuthorBlocked(userId: String, blocked: Boolean) {
        _blockedAuthorIds.update { if (blocked) it + userId else it - userId }
    }

    fun clearLocalRestrictions() {
        restrictionsGeneration++
        _blockedAuthorIds.value = emptySet()
        _hiddenPostIds.value = emptySet()
        pollSnapshots.clear()
        _changes.tryEmit(PostChange.Reset)
    }

    suspend fun hidePost(id: String) {
        val expected = restrictionsGeneration
        api.hidePost(id)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (expected == restrictionsGeneration) _hiddenPostIds.update { it + id }
    }

    suspend fun votePost(postId: String, value: Int) = postVoteLock(postId).withLock {
        api.votePost(postId, VoteRequest(value)).vote.toDomain().also { _changes.emit(PostChange.Voted(postId, it)) }
    }

    suspend fun votePoll(postId: String, poll: PostPoll, optionId: String) {
        val lock = pollVoteLocks.getOrPut(postId) { Mutex() }
        if (!lock.tryLock()) return
        val original = pollSnapshots[postId] ?: poll
        val optimistic = original.withOptimisticVote(optionId)
        if (optimistic == original) { lock.unlock(); return }
        val generation = restrictionsGeneration
        var settled = false
        try {
            publishPoll(postId, optimistic)
            val result = api.votePoll(postId, PollVoteRequest(optionId)).poll.withUiEntityOffsets()
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (generation == restrictionsGeneration) { publishPoll(postId, result); settled = true }
        } catch (error: Exception) {
            if (error !is CancellationException && generation == restrictionsGeneration) {
                // A lost response may follow a committed vote. Reconcile before offering a retry.
                val saved = try { api.getPost(postId).post.poll?.withUiEntityOffsets() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                if (saved?.hasVoted == true && generation == restrictionsGeneration) {
                    publishPoll(postId, saved)
                    settled = true
                    return
                }
            }
            throw error
        } finally {
            try {
                // Restore every screen even when cancellation interrupts reconciliation.
                if (!settled && generation == restrictionsGeneration) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { publishPoll(postId, original) }
                }
            } finally { lock.unlock() }
        }
    }

    private suspend fun publishPoll(postId: String, poll: PostPoll) {
        pollSnapshots[postId] = poll
        _changes.emit(PostChange.PollChanged(postId, poll))
    }

    private fun Post.withUiEntityOffsets(): Post {
        val incoming = poll?.withUiEntityOffsets()
        val latest = pollSnapshots[id]
        val resolved = if (latest != null && (latest.isSending || latest.hasVoted && incoming?.hasVoted == false)) latest else incoming
        if (resolved != null) pollSnapshots[id] = resolved
        return copy(entities = entities.toUiEntityOffsets(body), poll = resolved)
    }

    // ── Comments ───────────────────────────────────────────────────────────────

    suspend fun getComments(postId: String): List<Comment> =
        api.getComments(postId).comments.map { it.withUiEntityOffsets() }

    suspend fun createComment(
        postId: String,
        clientId: String,
        body: String,
        entities: List<MessageEntity> = emptyList(),
        replyToCommentId: String? = null,
        media: ImageMeta? = null,
    ): Comment = api.createComment(
        postId,
        CreateCommentRequest(
            clientId = clientId,
            body = body,
            entities = entities.toApiEntityOffsets(body),
            replyToCommentId = replyToCommentId,
            media = media,
        ),
    ).comment.withUiEntityOffsets().also { klipy.reportShare(media) }

    suspend fun updateComment(
        postId: String,
        commentId: String,
        body: String,
        entities: List<MessageEntity>,
    ) = api.updateComment(
        postId,
        commentId,
        UpdateCommentRequest(body, entities.toApiEntityOffsets(body)),
    )

    suspend fun deleteComment(postId: String, commentId: String) =
        api.deleteComment(postId, commentId)

    suspend fun voteComment(postId: String, commentId: String, value: Int) =
        commentVoteLock(postId, commentId).withLock {
            api.voteComment(postId, commentId, VoteRequest(value)).vote.toDomain()
        }

    suspend fun report(targetType: String, targetId: String, reason: String) =
        reportsApi.report(ReportRequest(targetType = targetType, targetId = targetId, reason = reason))

    // ── Utils ──────────────────────────────────────────────────────────────────

    private fun postVoteLock(postId: String): Mutex =
        postVoteLocks.getOrPut(postId) { Mutex() }

    private fun commentVoteLock(postId: String, commentId: String): Mutex =
        commentVoteLocks.getOrPut("$postId|$commentId") { Mutex() }

}

private fun PostPoll.withUiEntityOffsets(): PostPoll = copy(options = options.map {
    it.copy(entities = it.entities.toUiEntityOffsets(it.text))
})

private fun Comment.withUiEntityOffsets(): Comment =
    copy(entities = entities.toUiEntityOffsets(body))
