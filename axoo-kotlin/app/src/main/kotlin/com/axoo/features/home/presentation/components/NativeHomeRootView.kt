package com.axoo.features.home.presentation.components

import android.annotation.SuppressLint
import android.content.Context
import com.axoo.features.posts.presentation.components.PostFeedView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.axoo.application.navigation.components.NativeBottomNavigationBar
import com.axoo.application.navigation.route.BottomTab
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.engine.emoji.list.withSurfaceFullyVisible
import com.axoo.features.home.domain.HomeFeedTab
import com.axoo.features.home.presentation.HomeEvent
import com.axoo.features.home.presentation.HomeUiState
import com.axoo.ui.components.AxooRefreshLayout
import com.axoo.ui.components.BottomFadeView
import com.axoo.ui.theme.AppFontFamily
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.applyAxooTypography

@SuppressLint("ViewConstructor")
internal class NativeHomeRootView(context: Context) : FrameLayout(context) {
    private var eventSink: (HomeEvent) -> Unit = {}
    private var statusTopInset: Int = 0
    private var navigationBottomInset: Int = 0
    private var released = false
    private var playbackGate: EmojiPlaybackGate = EmojiPlaybackGate.Inactive
    private var pagerIdle = true

    private val appBar = NativeHomeAppBarView(context)
    private val hotFeed = PostFeedView(context)
    private val newFeed = PostFeedView(context)
    private val hotRefresh = createRefreshContainer(hotFeed)
    private val newRefresh = createRefreshContainer(newFeed)
    private val pager = ViewPager2(context).apply {
        adapter = FeedPagerAdapter(hotRefresh, newRefresh)
        offscreenPageLimit = 1
        overScrollMode = View.OVER_SCROLL_NEVER
        registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                eventSink(HomeEvent.FeedTabSelected(if (position == 0) HomeFeedTab.Hot else HomeFeedTab.New))
                applyPagedPlaybackGate()
            }

            override fun onPageScrollStateChanged(state: Int) {
                val idle = state == ViewPager2.SCROLL_STATE_IDLE
                if (pagerIdle == idle) return
                pagerIdle = idle
                applyPagedPlaybackGate()
            }
        })
    }
    private val statusIconView = com.axoo.engine.emoji.animation.drawable.AnimatedLottieView(
        context,
        android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 64f, context.resources.displayMetrics).toInt()
    ).apply {
        visibility = GONE
    }
    private val statusTextView = TextView(context).apply {
        gravity = android.view.Gravity.CENTER
        textSize = 22f
        applyAxooTypography(
            family = AppFontFamily.BALOO2,
            weight = 700,
        )
        setTextColor(NativeColors.textPrimary)
        includeFontPadding = false
    }
    private val statusSubtitleView = TextView(context).apply {
        gravity = android.view.Gravity.CENTER
        textSize = 14f
        applyAxooTypography(
            family = AppFontFamily.INTER,
            weight = 400,
        )
        setTextColor(NativeColors.textSecondary)
        includeFontPadding = false
        visibility = GONE
    }
    private val statusView = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        gravity = android.view.Gravity.CENTER
        val dp120 = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 120f, context.resources.displayMetrics).toInt()
        val dp12 = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 12f, context.resources.displayMetrics).toInt()
        val dp4 = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 4f, context.resources.displayMetrics).toInt()
        addView(statusIconView, android.widget.LinearLayout.LayoutParams(dp120, dp120).apply { bottomMargin = dp12 })
        addView(statusTextView, android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        addView(statusSubtitleView, android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp4 })
    }
    private val bottomBar = NativeBottomNavigationBar(context)
    private val bottomFade = BottomFadeView(context)

    init {
        setBackgroundColor(NativeColors.surface)
        clipChildren = false

        appBar.attachTo(pager)
        hotFeed.setCallbacks(
            onLoadMore = { eventSink(HomeEvent.LoadMoreHot) },
            onPostOpen = { eventSink(HomeEvent.PostOpened(it)) },
            onPostUpvote = { eventSink(HomeEvent.PostUpvoteTapped(it)) },
            onPostDownvote = { eventSink(HomeEvent.PostDownvoteTapped(it)) },
            onPollVote = { post, option -> eventSink(HomeEvent.PollOptionTapped(post, option)) },
            onPostComment = { eventSink(HomeEvent.PostCommentTapped(it)) },
            onPostMenu = { post, _ -> eventSink(HomeEvent.PostMenuTapped(post)) },
            onAuthor = { eventSink(HomeEvent.AuthorTapped(it)) },
        )
        newFeed.setCallbacks(
            onLoadMore = { eventSink(HomeEvent.LoadMoreNew) },
            onPostOpen = { eventSink(HomeEvent.PostOpened(it)) },
            onPostUpvote = { eventSink(HomeEvent.PostUpvoteTapped(it)) },
            onPostDownvote = { eventSink(HomeEvent.PostDownvoteTapped(it)) },
            onPollVote = { post, option -> eventSink(HomeEvent.PollOptionTapped(post, option)) },
            onPostComment = { eventSink(HomeEvent.PostCommentTapped(it)) },
            onPostMenu = { post, _ -> eventSink(HomeEvent.PostMenuTapped(post)) },
            onAuthor = { eventSink(HomeEvent.AuthorTapped(it)) },
        )
        bottomBar.setOnTabSelected { eventSink(HomeEvent.BottomTabSelected(it)) }

        addView(appBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(pager, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(statusView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(bottomFade, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(bottomBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            if (statusTopInset != statusTop) {
                statusTopInset = statusTop
                appBar.setStatusTopInset(statusTop)
            }
            if (navigationBottomInset != navigationBottom) {
                navigationBottomInset = navigationBottom
                bottomBar.setNavigationBottomInset(navigationBottom)
            }
            insets
        }
        ViewCompat.requestApplyInsets(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { ViewCompat.requestApplyInsets(this) }
    }

    fun setEventSink(callback: (HomeEvent) -> Unit) {
        if (released) return
        eventSink = callback
    }

    fun runAfterSelectedFeedContentDrawn(callback: () -> Unit) {
        if (released) return
        val selectedFeed = if (pager.currentItem == 0) hotFeed else newFeed
        selectedFeed.runAfterContentDrawn(callback)
    }

    fun bindNotificationsCount(count: Int) = bottomBar.bindUnreadCount(count)

    fun render(uiState: HomeUiState) {
        if (released) return
        setBackgroundColor(NativeColors.surface)

        val selectedIndex = if (uiState.selectedFeedTab == HomeFeedTab.Hot) 0 else 1
        appBar.bind(selectedIndex, uiState.newFeedUnreadCount)
        hotRefresh.isRefreshing = uiState.isRefreshing && selectedIndex == 0
        newRefresh.isRefreshing = uiState.isRefreshing && selectedIndex == 1
        if (pager.currentItem != selectedIndex) {
            pager.setCurrentItem(selectedIndex, true)
        }
        bottomBar.bind(BottomTab.HOME)

        hotFeed.render(
            posts = uiState.hotFeed.posts,
            hasMore = uiState.hotFeed.hasMore,
            isLoading = uiState.hotFeed.isLoading,
            isLoadingMore = uiState.hotFeed.isLoadingMore,
            scrollToTopSignal = uiState.scrollToTopHot,
        )
        newFeed.render(
            posts = uiState.newFeed.posts,
            hasMore = uiState.newFeed.hasMore,
            isLoading = uiState.newFeed.isLoading,
            isLoadingMore = uiState.newFeed.isLoadingMore,
            scrollToTopSignal = uiState.scrollToTopNew,
        )

        val selectedFeed = uiState.selectedFeed
        statusTextView.applyAxooTypography(
            family = AppFontFamily.BALOO2,
            weight = if (selectedFeed.isEmpty) 800 else 700,
        )
        
        if (selectedFeed.isError) {
            statusTextView.text = "Feed non trovato"
            statusSubtitleView.text = "C'è stato un problema di connessione.\nTira verso il basso per riprovare."
            statusSubtitleView.visibility = VISIBLE
            statusIconView.visibility = VISIBLE
            statusIconView.setAnimation(
                id = "error_angry",
                path = "axooji/animations/focaccia-peroni/angry-2.tgs"
            )
        } else if (selectedFeed.isEmpty && !selectedFeed.isLoading) {
            statusTextView.text = "Qui è ancora tutto vuoto."
            statusSubtitleView.text = ""
            statusSubtitleView.visibility = GONE
            statusIconView.visibility = GONE
        } else {
            statusTextView.text = ""
            statusSubtitleView.text = ""
            statusSubtitleView.visibility = GONE
            statusIconView.visibility = GONE
        }
        
        statusView.visibility = if (statusTextView.text.isNullOrBlank()) GONE else VISIBLE
    }

    /** Phase 2 — fans the gate out to both feeds. */
    fun setScreenPlaybackGate(gate: EmojiPlaybackGate) {
        if (released) return
        playbackGate = gate
        applyPagedPlaybackGate()
    }

    private fun applyPagedPlaybackGate() {
        if (released) return
        hotFeed.setScreenPlaybackGate(playbackGate.forFeedPage(page = 0))
        newFeed.setScreenPlaybackGate(playbackGate.forFeedPage(page = 1))
    }

    private fun createRefreshContainer(feed: PostFeedView): AxooRefreshLayout =
        AxooRefreshLayout(
            context = context,
            content = feed,
            canChildScrollUp = feed::canScrollUp,
        ).apply {
            setOnRefreshListener { eventSink(HomeEvent.RefreshRequested) }
        }

    private fun EmojiPlaybackGate.forFeedPage(page: Int): EmojiPlaybackGate =
        withSurfaceFullyVisible(pagerIdle && pager.currentItem == page)

    fun releaseViewResourcesForTrim() {
        releaseViewResources()
    }

    fun release() {
        releaseViewResources()
    }

    private fun releaseViewResources() {
        if (released) return
        released = true
        appBar.release()
        hotFeed.releaseViewResourcesForTrim()
        newFeed.releaseViewResourcesForTrim()
        pager.adapter = null
        eventSink = {}
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val exactWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val atMostHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)

        appBar.measure(exactWidth, atMostHeight)
        bottomBar.measure(exactWidth, atMostHeight)
        val contentHeight = (height - appBar.measuredHeight).coerceAtLeast(0)
        bottomFade.measure(exactWidth, MeasureSpec.makeMeasureSpec(
            (bottomBar.measuredHeight + bottomFade.fadeHeightPx).coerceAtMost(contentHeight), MeasureSpec.EXACTLY))
        val contentSpec = MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY)
        pager.measure(exactWidth, contentSpec)
        statusView.measure(exactWidth, contentSpec)
        hotFeed.setBottomNavigationPadding(bottomBar.measuredHeight)
        newFeed.setBottomNavigationPadding(bottomBar.measuredHeight)

        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        val appBarHeight = appBar.measuredHeight
        val bottomBarHeight = bottomBar.measuredHeight
        appBar.layout(0, 0, width, appBarHeight)
        pager.layout(0, appBarHeight, width, height)
        statusView.layout(0, appBarHeight, width, height)
        bottomFade.layout(0, height - bottomFade.measuredHeight, width, height)
        bottomBar.layout(0, height - bottomBarHeight, width, height)
    }

    private class FeedPagerAdapter(
        private val hotFeed: View,
        private val newFeed: View,
    ) : RecyclerView.Adapter<FeedPageHolder>() {
        override fun getItemCount(): Int = 2

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedPageHolder {
            return FeedPageHolder(
                FrameLayout(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                },
            )
        }

        override fun onBindViewHolder(holder: FeedPageHolder, position: Int) {
            val feed = if (position == 0) hotFeed else newFeed
            (feed.parent as? ViewGroup)?.removeView(feed)
            holder.container.removeAllViews()
            holder.container.addView(
                feed,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
            )
        }
    }

    private class FeedPageHolder(
        val container: FrameLayout,
    ) : RecyclerView.ViewHolder(container)

}
