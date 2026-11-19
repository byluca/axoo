package com.axoo.features.home.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axoo.features.home.domain.HomeFeedTab
import com.axoo.features.posts.data.PostChange
import com.axoo.features.posts.data.PostRepository
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.domain.desiredVote
import com.axoo.features.posts.domain.withOptimisticVote
import com.axoo.features.posts.domain.withServerVote
import com.axoo.features.profile.data.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val postRepo: PostRepository,
    private val profileRepo: ProfileRepository,
) : ViewModel() {
    private companion object {
        const val TAG = "FeedViewModel"
    }

    private val _state = MutableStateFlow(
        HomeUiState(
            hotFeed = HomeFeedUiState(isLoading = true),
            newFeed = HomeFeedUiState(isLoading = true),
        ),
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val feedRequestJobs = mutableMapOf<HomeFeedTab, Job>()
    private val postVoteJobs = mutableMapOf<String, Job>()
    private var recentlyPublishedPost: Post? = null

    init {
        viewModelScope.launch {
            postRepo.changes.collect { change ->
                if (change is PostChange.PollChanged) {
                    _state.update { state -> state.copy(
                        hotFeed = state.hotFeed.copy(posts = state.hotFeed.posts.map { if (it.id == change.id) it.copy(poll = change.poll) else it }),
                        newFeed = state.newFeed.copy(posts = state.newFeed.posts.map { if (it.id == change.id) it.copy(poll = change.poll) else it }),
                    ) }
                    recentlyPublishedPost = recentlyPublishedPost?.let { if (it.id == change.id) it.copy(poll = change.poll) else it }
                }
            }
        }
        viewModelScope.launch {
            postRepo.blockedAuthorIds.collect { blocked ->
                _state.update { state -> state.copy(
                    hotFeed = state.hotFeed.copy(posts = state.hotFeed.posts.filterNot { it.userId in blocked }),
                    newFeed = state.newFeed.copy(posts = state.newFeed.posts.filterNot { it.userId in blocked }),
                ) }
                if (recentlyPublishedPost?.userId in blocked) recentlyPublishedPost = null
            }
        }
        viewModelScope.launch { postRepo.hiddenPostIds.collect { ids -> ids.forEach(::removePostFromFeeds) } }
        loadCurrentUserId()
        loadInitialFeeds()
    }

    // ── Caricamento iniziale / refresh ─────────────────────────────────────────

    private fun loadCurrentUserId() {
        viewModelScope.launch {
            val cached = profileRepo.getCachedUser()?.id

            if (cached != null) {
                _state.update { it.copy(currentUserId = cached) }
                return@launch
            }

            runCatching {
                profileRepo.getMe().id
            }.onSuccess { id ->
                _state.update { it.copy(currentUserId = id) }
            }.onFailure { e ->
                Log.e(TAG, "Unable to resolve current user id", e)
            }
        }
    }

    fun selectFeedTab(tab: HomeFeedTab) {
        val changed = _state.value.selectedFeedTab != tab
        _state.update { it.copy(selectedFeedTab = tab) }
        if (changed && tab == HomeFeedTab.New) {
            loadNew()
        }
    }

    fun loadInitialFeeds() {
        loadHot()
        loadNew()
        refreshNewFeedUnreadCount()
    }

    fun loadHot(clearCurrent: Boolean = false) =
        loadFeed(HomeFeedTab.Hot, clearCurrent)

    fun loadNew(clearCurrent: Boolean = false) =
        loadFeed(HomeFeedTab.New, clearCurrent)

    private fun loadFeed(tab: HomeFeedTab, clearCurrent: Boolean) {
        feedRequestJobs[tab]?.cancel()
        updateFeed(tab) { it.copy(isLoadingMore = false) }
        feedRequestJobs[tab] = viewModelScope.launch {
            try {
                val current = _state.value.feed(tab)
                if ((clearCurrent && current.posts.isEmpty()) || current.isError) {
                    updateFeed(tab) { HomeFeedUiState(isLoading = true) }
                }

                val page = postRepo.getPostsPage(order = tab.apiValue)
                updateFeed(tab) {
                    HomeFeedUiState(
                        posts = (if (tab == HomeFeedTab.New) mergeRecentlyPublished(page.posts) else page.posts)
                            .filterNot { it.userId in postRepo.blockedAuthorIds.value || it.id in postRepo.hiddenPostIds.value },
                        nextCursor = page.nextCursor,
                    )
                }
                if (tab == HomeFeedTab.New && _state.value.selectedFeedTab == HomeFeedTab.New) {
                    page.posts.firstOrNull()?.createdAt?.let(::markNewFeedSeen)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Unable to load ${tab.apiValue} feed", e)
                updateFeed(tab) {
                    if (it.posts.isEmpty()) HomeFeedUiState(isError = true)
                    else it.copy(isLoading = false, isLoadingMore = false)
                }
            } finally {
                if (tab == HomeFeedTab.Hot) _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    // ── Pagination ─────────────────────────────────────────────────────────────

    fun loadMoreHot() = loadMore(HomeFeedTab.Hot)

    fun loadMoreNew() = loadMore(HomeFeedTab.New)

    private fun loadMore(tab: HomeFeedTab) {
        val feed = _state.value.feed(tab)
        val cursor = feed.nextCursor ?: return
        if (feed.isLoading || feed.isLoadingMore || feedRequestJobs[tab]?.isActive == true) return
        updateFeed(tab) { it.copy(isLoadingMore = true) }
        feedRequestJobs[tab] = viewModelScope.launch {
            try {
                val page = postRepo.getPostsPage(
                    order = tab.apiValue,
                    cursor = cursor,
                )
                updateFeed(tab) {
                    it.copy(
                        posts = (it.posts + page.posts).distinctBy(Post::id).filterNot { post -> post.userId in postRepo.blockedAuthorIds.value },
                        nextCursor = page.nextCursor,
                        isLoading = false,
                        isLoadingMore = false,
                        isError = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                updateFeed(tab) { it.copy(isLoadingMore = false) }
            }
        }
    }

    // ── Update ottimistico voto ────────────────────────────────────────────────

    fun updatePostInFeeds(updated: Post) {
        fun updateList(list: List<Post>) = list.map { if (it.id == updated.id) updated else it }
        _state.update {
            it.copy(
                hotFeed = it.hotFeed.copy(posts = updateList(it.hotFeed.posts)),
                newFeed = it.newFeed.copy(posts = updateList(it.newFeed.posts)),
            )
        }
    }

    fun removePostFromFeeds(postId: String) {
        fun removeFrom(list: List<Post>) = list.filterNot { it.id == postId }
        _state.update {
            it.copy(
                hotFeed = it.hotFeed.copy(posts = removeFrom(it.hotFeed.posts)),
                newFeed = it.newFeed.copy(posts = removeFrom(it.newFeed.posts)),
            )
        }
    }

    fun hidePost(postId: String, onFailure: () -> Unit) {
        viewModelScope.launch {
            try { postRepo.hidePost(postId); removePostFromFeeds(postId) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { onFailure() }
        }
    }

    fun deletePost(postId: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { postRepo.deletePost(postId) }
            if (result.isSuccess) removePostFromFeeds(postId)
            onResult(result)
        }
    }

    fun votePoll(post: Post, optionId: String, onFailure: () -> Unit) {
        val poll = (currentPost(post.id) ?: post).poll ?: return
        if (poll.hasVoted || poll.isSending) return
        viewModelScope.launch {
            try { postRepo.votePoll(post.id, poll, optionId) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { onFailure() }
        }
    }

    fun votePost(post: Post, type: String) {
        val current = currentPost(post.id) ?: post
        val next = desiredVote(current.myVote, type)
        val updated = current.withOptimisticVote(next)
        updatePostInFeeds(updated)

        postVoteJobs.remove(post.id)?.cancel()
        postVoteJobs[post.id] = viewModelScope.launch {
            val runningJob = coroutineContext[Job]
            try {
                val result = runCatching { postRepo.votePost(post.id, next.toInt) }
                val error = result.exceptionOrNull()
                if (error is CancellationException) throw error
                result.onSuccess { serverVote ->
                    val latest = currentPost(post.id) ?: return@onSuccess
                    if (latest.myVote == next) updatePostInFeeds(latest.withServerVote(serverVote))
                }.onFailure {
                    val latest = currentPost(post.id) ?: return@onFailure
                    if (latest.myVote == next) updatePostInFeeds(latest.copy(
                        upvotes = current.upvotes, downvotes = current.downvotes, myVoteRaw = current.myVoteRaw,
                    ))
                }
            } finally {
                if (postVoteJobs[post.id] === runningJob) postVoteJobs.remove(post.id)
            }
        }
    }

    private fun currentPost(postId: String): Post? {
        val hot = _state.value.hotFeed.posts.firstOrNull { it.id == postId }
        if (hot != null) return hot
        return _state.value.newFeed.posts.firstOrNull { it.id == postId }
    }

    fun reportPost(
        postId: String,
        reason: String,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { postRepo.report("post", postId, reason) }
                .onSuccess { onSuccess() }
                .onFailure(onFailure)
        }
    }

    fun blockUser(userId: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { profileRepo.blockUser(userId) }
            if (result.isSuccess) {
                fun removeAuthor(list: List<Post>) = list.filterNot { it.userId == userId }
                _state.update {
                    it.copy(
                        hotFeed = it.hotFeed.copy(posts = removeAuthor(it.hotFeed.posts)),
                        newFeed = it.newFeed.copy(posts = removeAuthor(it.newFeed.posts)),
                    )
                }
            }
            onResult(result)
        }
    }

    fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }
        loadHot(clearCurrent = true)
        loadNew()
        if (_state.value.selectedFeedTab == HomeFeedTab.Hot) refreshNewFeedUnreadCount()
    }

    fun refreshNewFeedUnreadCount() {
        viewModelScope.launch {
            runCatching { postRepo.getNewFeedUnreadCount() }
                .onSuccess { unreadCount ->
                    _state.update { it.copy(newFeedUnreadCount = unreadCount) }
                }
                .onFailure { e -> Log.w(TAG, "Unable to load new feed unread count", e) }
        }
    }

    private fun markNewFeedSeen(seenThrough: String) {
        viewModelScope.launch {
            runCatching { postRepo.markNewFeedSeen(seenThrough) }
                .onSuccess { refreshNewFeedUnreadCount() }
                .onFailure { e ->
                    Log.w(TAG, "Unable to mark new feed as seen", e)
                    refreshNewFeedUnreadCount()
                }
        }
    }

    // ── Notifica "nuovo post pubblicato" → scroll in cima al tab New ───────────

    fun notifyNewPostPublished(post: Post) {
        recentlyPublishedPost = post
        _state.update {
            it.copy(
                selectedFeedTab = HomeFeedTab.New,
                newFeed = it.newFeed.copy(
                    posts = listOf(post) + it.newFeed.posts.filterNot { current -> current.id == post.id },
                    isLoading = false,
                    isError = false,
                ),
                scrollToTopNew = it.scrollToTopNew + 1,
            )
        }
        loadNew()
    }

    private fun mergeRecentlyPublished(posts: List<Post>): List<Post> {
        val published = recentlyPublishedPost ?: return posts
        return if (posts.any { it.id == published.id }) {
            posts
        } else {
            listOf(published) + posts
        }
    }

    private fun HomeUiState.feed(tab: HomeFeedTab): HomeFeedUiState = when (tab) {
        HomeFeedTab.Hot -> hotFeed
        HomeFeedTab.New -> newFeed
    }

    private fun updateFeed(tab: HomeFeedTab, transform: (HomeFeedUiState) -> HomeFeedUiState) {
        _state.update { state ->
            when (tab) {
                HomeFeedTab.Hot -> state.copy(hotFeed = transform(state.hotFeed))
                HomeFeedTab.New -> state.copy(newFeed = transform(state.newFeed))
            }
        }
    }
}
