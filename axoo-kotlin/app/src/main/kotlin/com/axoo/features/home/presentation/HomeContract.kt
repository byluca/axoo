package com.axoo.features.home.presentation

import com.axoo.application.navigation.route.Tab
import com.axoo.features.home.domain.HomeFeedTab
import com.axoo.features.posts.domain.Post

data class HomeFeedUiState(
    val posts: List<Post> = emptyList(),
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isError: Boolean = false,
) {
    val hasMore: Boolean
        get() = nextCursor != null

    val isEmpty: Boolean
        get() = posts.isEmpty() && !hasMore && !isLoading && !isError
}

data class HomeUiState(
    val selectedFeedTab: HomeFeedTab = HomeFeedTab.Hot,
    val hotFeed: HomeFeedUiState = HomeFeedUiState(),
    val newFeed: HomeFeedUiState = HomeFeedUiState(),
    val currentUserId: String? = null,
    val isRefreshing: Boolean = false,
    val scrollToTopHot: Int = 0,
    val scrollToTopNew: Int = 0,
    val newFeedUnreadCount: Int = 0,
) {
    val selectedFeed: HomeFeedUiState
        get() = if (selectedFeedTab == HomeFeedTab.Hot) hotFeed else newFeed
}

sealed interface HomeEvent {
    data object RefreshRequested : HomeEvent
    data object LoadMoreHot : HomeEvent
    data object LoadMoreNew : HomeEvent
    data object ScrollToNewRequested : HomeEvent
    data class FeedTabSelected(val tab: HomeFeedTab) : HomeEvent
    data class BottomTabSelected(val tab: Tab) : HomeEvent
    data class PostOpened(val post: Post) : HomeEvent
    data class AuthorTapped(val userId: String) : HomeEvent
    data class PostCommentTapped(val post: Post) : HomeEvent
    data class PostUpvoteTapped(val post: Post) : HomeEvent
    data class PostDownvoteTapped(val post: Post) : HomeEvent
    data class PollOptionTapped(val post: Post, val optionId: String) : HomeEvent
    data class PostMenuTapped(val post: Post) : HomeEvent
}
