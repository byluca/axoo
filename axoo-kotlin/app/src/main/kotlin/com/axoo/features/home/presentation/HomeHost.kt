package com.axoo.features.home.presentation

import android.content.Context
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.axoo.application.navigation.host.BackHandler
import com.axoo.application.navigation.route.Tab
import com.axoo.application.navigation.route.NativeScreen
import com.axoo.application.navigation.route.Route
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.features.home.domain.HomeFeedTab
import com.axoo.features.home.presentation.components.NativeHomeRootView
import com.axoo.features.posts.domain.Post
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class HomeHost(
    context: Context,
    private val viewModel: FeedViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val notifications: com.axoo.features.notifications.presentation.NotificationsViewModel? = null,
) : FrameLayout(context), NativeScreen, BackHandler {
    override val route: Route = Route.Home
    private val rootView = NativeHomeRootView(context)

    private var onNavigate: (Tab) -> Unit = {}
    private var onPostOpen: (Post) -> Unit = {}
    private var onAuthor: (String) -> Unit = {}
    private var onPostMenu: (Post, Boolean) -> Unit = { _, _ -> }

    private var observersStarted: Boolean = false
    private var observersJob: Job? = null

    init {
        addView(rootView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        rootView.setEventSink { event ->
            when (event) {
                HomeEvent.RefreshRequested -> viewModel.refresh()
                HomeEvent.LoadMoreHot -> viewModel.loadMoreHot()
                HomeEvent.LoadMoreNew -> viewModel.loadMoreNew()
                HomeEvent.ScrollToNewRequested -> viewModel.selectFeedTab(HomeFeedTab.New)
                is HomeEvent.FeedTabSelected -> viewModel.selectFeedTab(event.tab)
                is HomeEvent.BottomTabSelected -> {
                    if (event.tab != Tab.Home) {
                        onNavigate(event.tab)
                    }
                }
                is HomeEvent.PostOpened -> onPostOpen(event.post)
                is HomeEvent.AuthorTapped -> onAuthor(event.userId)
                is HomeEvent.PostCommentTapped -> onPostOpen(event.post)
                is HomeEvent.PostUpvoteTapped -> viewModel.votePost(event.post, "upvote")
                is HomeEvent.PostDownvoteTapped -> viewModel.votePost(event.post, "downvote")
                is HomeEvent.PollOptionTapped -> viewModel.votePoll(event.post, event.optionId) {
                    com.axoo.ui.sheets.OverlayCard.error(this, "Voto non inviato. Riprova.")
                }
                is HomeEvent.PostMenuTapped -> {
                    val post = event.post
                    val currentUserId = viewModel.state.value.currentUserId
                    onPostMenu(post, currentUserId != null && post.userId == currentUserId)
                }
            }
        }
    }

    fun setCallbacks(
        onNavigate: (Tab) -> Unit,
        onPostOpen: (Post) -> Unit,
        onPostMenu: (Post, Boolean) -> Unit,
        onAuthor: (String) -> Unit = {},
    ) {
        this.onNavigate = onNavigate
        this.onPostOpen = onPostOpen
        this.onPostMenu = onPostMenu
        this.onAuthor = onAuthor
    }

    override fun onBackPressed(): Boolean = false

    private fun startObservers() {
        if (observersStarted) return
        observersStarted = true

        observersJob = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                notifications?.let { vm -> launch { vm.unread.collect(rootView::bindNotificationsCount) } }
                viewModel.state.collect(rootView::render)
            }
        }
    }

    private fun stopObservers() {
        observersJob?.cancel()
        observersJob = null
        observersStarted = false
    }

    override fun onScreenTransitionStart(isOpening: Boolean, isBackward: Boolean) {
        // Freeze playback the instant the transition begins — both incoming and
        // outgoing screens go through this path.
        rootView.setScreenPlaybackGate(EmojiPlaybackGate.ActiveFrozen)
    }

    override fun onInteractiveTransitionStart(isOpening: Boolean) {
        rootView.setScreenPlaybackGate(EmojiPlaybackGate.InteractiveTransition)
    }

    override fun onScreenFullyVisible() {
        startObservers()
        notifications?.refreshUnread()
        rootView.setScreenPlaybackGate(EmojiPlaybackGate.FullyVisible)
    }

    override fun onScreenHidden() {
        stopObservers()
        rootView.setScreenPlaybackGate(EmojiPlaybackGate.Inactive)
    }

    override fun onScreenDestroyed() {
        stopObservers()
        rootView.release()
    }
}
