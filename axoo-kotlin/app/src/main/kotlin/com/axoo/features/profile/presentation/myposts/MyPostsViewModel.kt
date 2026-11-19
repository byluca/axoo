package com.axoo.features.profile.presentation.myposts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axoo.features.posts.data.PostChange
import com.axoo.features.posts.data.PostRepository
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.domain.desiredVote
import com.axoo.features.posts.domain.withOptimisticVote
import com.axoo.features.posts.domain.withServerVote
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyPostsState(
    val posts: List<Post> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: Boolean = false,
    val failedLoadMore: Boolean = false,
)

@HiltViewModel
class MyPostsViewModel @Inject constructor(
    private val posts: PostRepository,
    savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val authorId: String? = savedState["authorId"]
    private val _state = MutableStateFlow(MyPostsState())
    val state = _state.asStateFlow()
    private var request: Job? = null
    private val voting = mutableMapOf<String, Job>()
    private val revisions = mutableMapOf<String, Int>()
    private val created = mutableSetOf<String>()
    private val removed = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            posts.changes.collect { change ->
                if (change == PostChange.Reset) {
                    request?.cancel()
                    request = null
                    voting.values.forEach { it.cancel() }
                    voting.clear()
                    revisions.clear()
                    created.clear()
                    removed.clear()
                    _state.value = MyPostsState()
                    return@collect
                }
                if (change is PostChange.Created && authorId != null && change.post.userId != authorId) return@collect
                val id = when (change) {
                    is PostChange.Created -> change.post.id.also { created.add(it) }
                    is PostChange.Updated -> change.post.id
                    is PostChange.Deleted -> change.id.also { removed.add(it) }
                    is PostChange.Voted -> change.id
                    is PostChange.PollChanged -> change.id
                    PostChange.Reset -> return@collect
                }
                revisions[id] = (revisions[id] ?: 0) + 1
                _state.update { state ->
                    when (change) {
                        PostChange.Reset -> state
                        is PostChange.Created -> state.copy(posts = listOf(change.post) + state.posts.filterNot { it.id == change.post.id })
                        is PostChange.Updated -> state.copy(posts = state.posts.map { if (it.id == change.post.id) change.post else it })
                        is PostChange.Deleted -> state.copy(posts = state.posts.filterNot { it.id == change.id })
                        is PostChange.PollChanged -> state.copy(posts = state.posts.map { if (it.id == change.id) it.copy(poll = change.poll) else it })
                        is PostChange.Voted -> state.copy(posts = state.posts.map { if (it.id == change.id) it.withServerVote(change.vote) else it })
                    }
                }
            }
        }
        if (authorId != null) {
            viewModelScope.launch {
                posts.blockedAuthorIds.collect { blocked ->
                    _state.update { state -> state.copy(posts = state.posts.filterNot { it.userId in blocked }) }
                }
            }
            viewModelScope.launch {
                posts.hiddenPostIds.collect { hidden ->
                    _state.update { state -> state.copy(posts = state.posts.filterNot { it.id in hidden }) }
                }
            }
        }
        refresh()
    }

    fun ensureLoaded() { if (request?.isActive != true) refresh() }

    fun retry() { if (state.value.failedLoadMore) loadMore() else refresh() }

    fun refresh() {
        request?.cancel()
        load(null)
    }

    fun loadMore() {
        if (request?.isActive == true) return
        val cursor = state.value.nextCursor ?: return
        load(cursor)
    }

    private fun load(cursor: String?) {
        _state.update { it.copy(loading = cursor == null && it.posts.isEmpty(), loadingMore = cursor != null, error = false) }
        val expectedRevisions = revisions.toMap()
        request = viewModelScope.launch {
            try {
                val page = posts.getMyPostsPage(cursor, authorId = authorId)
                currentCoroutineContext().ensureActive()
                _state.update { state ->
                    val fetched = page.posts.map { post ->
                        if (revisions[post.id] != expectedRevisions[post.id]) state.posts.firstOrNull { it.id == post.id } ?: post else post
                    }
                    val recent = state.posts.filter { it.id in created && revisions[it.id] != expectedRevisions[it.id] }
                    state.copy(
                        posts = (if (cursor == null) recent + fetched else state.posts + fetched)
                            .filterNot { it.id in removed || authorId != null && (it.id in posts.hiddenPostIds.value || it.userId in posts.blockedAuthorIds.value) }.distinctBy(Post::id),
                        nextCursor = page.nextCursor, loading = false, loadingMore = false,
                        failedLoadMore = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { it.copy(loading = false, loadingMore = false, error = true, failedLoadMore = cursor != null) }
            }
        }
    }

    fun votePoll(post: Post, optionId: String, onFailure: () -> Unit) {
        val poll = (state.value.posts.firstOrNull { it.id == post.id } ?: post).poll ?: return
        if (poll.hasVoted || poll.isSending) return
        viewModelScope.launch {
            try { posts.votePoll(post.id, poll, optionId) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { onFailure() }
        }
    }

    fun vote(post: Post, direction: String) {
        if (voting[post.id]?.isActive == true) return
        val current = state.value.posts.firstOrNull { it.id == post.id } ?: post
        val next = desiredVote(current.myVote, direction)
        replace(current.withOptimisticVote(next))
        voting[post.id] = viewModelScope.launch {
            try {
                val result = posts.votePost(post.id, next.toInt)
                state.value.posts.firstOrNull { it.id == post.id }?.let { replace(it.withServerVote(result)) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.value.posts.firstOrNull { it.id == post.id }?.let { latest ->
                    replace(latest.copy(upvotes = current.upvotes, downvotes = current.downvotes, myVoteRaw = current.myVoteRaw))
                }
            } finally {
                voting.remove(post.id)
            }
        }
    }

    private fun replace(post: Post) {
        revisions[post.id] = (revisions[post.id] ?: 0) + 1
        _state.update { state -> state.copy(posts = state.posts.map { if (it.id == post.id) post else it }) }
    }
}
