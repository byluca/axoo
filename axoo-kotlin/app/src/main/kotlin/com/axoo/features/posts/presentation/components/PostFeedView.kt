/*
 * Mostra una lista di post con paginazione e riproduzione delle emoji.
 *
 * Si occupa di RecyclerView, caricamento progressivo e stato dello scroll.
 * La grafica della singola scheda è riutilizzata da postcomponents.
 */
package com.axoo.features.posts.presentation.components

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.axoo.engine.emoji.image.CoilBitmapLoader
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.engine.emoji.list.EmojiRecyclerView
import com.axoo.features.posts.domain.Post
import com.axoo.ui.avatar.buildAvatarUrl
import com.axoo.ui.components.NativeSkeletonCardView
import com.axoo.ui.theme.NativeColors

internal class PostFeedView(context: Context, header: View? = null) : FrameLayout(context) {
    private var onLoadMore: () -> Unit = {}
    private var onPostOpen: (Post) -> Unit = {}
    private var onPostUpvote: (Post) -> Unit = {}
    private var onPostDownvote: (Post) -> Unit = {}
    private var onPostComment: (Post) -> Unit = {}
    private var onPostMenu: (Post, Boolean) -> Unit = { _, _ -> }
    private var onAuthor: (String) -> Unit = {}
    private var onPollVote: (Post, String) -> Unit = { _, _ -> }
    private var hasMore = false
    private var lastScrollToTopSignal = 0
    private var pendingScrollToTopSignal: Int? = null
    private var released = false
    private var afterContentDrawn: (() -> Unit)? = null
    private var contentDrawCallbackScheduled = false

    private val adapter = NativePostAdapter(
        onOpen = { onPostOpen(it) },
        onUpvote = { onPostUpvote(it) },
        onDownvote = { onPostDownvote(it) },
        onComment = { onPostComment(it) },
        onMenu = { post, isOwn -> onPostMenu(post, isOwn) },
        onAuthor = { onAuthor(it) },
        onPollVote = { post, option -> onPollVote(post, option) },
    )
    private val scrollListener = NativeFeedScrollListener(
        adapter = adapter,
        totalItemCount = { adapter.postCount },
        hasMore = { hasMore },
        onLoadMore = { onLoadMore() },
        headerCount = if (header == null) 0 else 1,
    )
    private val recyclerView = EmojiRecyclerView(context).apply {
        prefetchNativeIcons(context.applicationContext, context.dp(48f).toInt())
        clipToPadding = false
        setAxooPadding(0, context.dp(4f).toInt(), 0, context.dp(16f).toInt())
        layoutManager = LinearLayoutManager(context)
        adapter = if (header == null) this@PostFeedView.adapter
            else ConcatAdapter(FeedHeaderAdapter(header), this@PostFeedView.adapter)
        addOnScrollListener(scrollListener)
    }
    private val skeletonView = NativeSkeletonCardView(context, rows = 4)

    init {
        // The refresh container behind this view is intentionally magenta. Keep the feed opaque
        // from its very first frame so that color is revealed only by an actual pull gesture.
        setBackgroundColor(NativeColors.surface)
        recyclerView.setBackgroundColor(NativeColors.surface)
        addView(
            recyclerView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        addView(
            skeletonView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        skeletonView.visibility = GONE
    }

    fun setBottomNavigationPadding(height: Int) {
        val bottom = height + context.dp(16f).toInt()
        if (recyclerView.paddingBottom == bottom) return
        recyclerView.setAxooPadding(0, recyclerView.paddingTop, 0, bottom)
    }

    fun setCallbacks(
        onLoadMore: () -> Unit,
        onPostOpen: (Post) -> Unit,
        onPostUpvote: (Post) -> Unit,
        onPostDownvote: (Post) -> Unit,
        onPostComment: (Post) -> Unit,
        onPostMenu: (Post, Boolean) -> Unit,
        onAuthor: (String) -> Unit = {},
        onPollVote: (Post, String) -> Unit = { _, _ -> },
    ) {
        this.onLoadMore = onLoadMore
        this.onPostOpen = onPostOpen
        this.onPostUpvote = onPostUpvote
        this.onPostDownvote = onPostDownvote
        this.onPostComment = onPostComment
        this.onPostMenu = onPostMenu
        this.onAuthor = onAuthor
        this.onPollVote = onPollVote
    }

    fun canScrollUp(): Boolean = recyclerView.canScrollVertically(-1)

    fun render(
        posts: List<Post>,
        hasMore: Boolean,
        isLoading: Boolean,
        isLoadingMore: Boolean,
        scrollToTopSignal: Int,
        insertAnimSignal: Int = 0,
        insertAnimPostId: String? = null,
    ) {
        if (released) return
        val showSkeleton = isLoading && posts.isEmpty()
        skeletonView.visibility = if (showSkeleton) VISIBLE else GONE
        recyclerView.visibility = if (showSkeleton) GONE else VISIBLE
        if (showSkeleton) {

            return
        }

        render(
            posts = posts,
            hasMore = hasMore,
            isLoadingMore = isLoadingMore,
            scrollToTopSignal = scrollToTopSignal,
            insertAnimSignal = insertAnimSignal,
            insertAnimPostId = insertAnimPostId,
        )
    }

    private fun render(
        posts: List<Post>,
        hasMore: Boolean,
        isLoadingMore: Boolean,
        scrollToTopSignal: Int,
        insertAnimSignal: Int,
        insertAnimPostId: String?,
    ) {
        this.hasMore = hasMore
        setBackgroundColor(NativeColors.surface)
        recyclerView.setBackgroundColor(NativeColors.surface)
        adapter.setInsertAnimation(insertAnimSignal, insertAnimPostId)
        adapter.setLoadingMore(isLoadingMore)
        if (scrollToTopSignal != lastScrollToTopSignal) {
            lastScrollToTopSignal = scrollToTopSignal
            pendingScrollToTopSignal = scrollToTopSignal
        }
        adapter.submitList(posts) {
            applyPendingScrollToTopIfReady()
            scheduleContentDrawCallbackIfReady()
        }
    }

    fun runAfterContentDrawn(callback: () -> Unit) {
        if (released) return
        afterContentDrawn = callback
        scheduleContentDrawCallbackIfReady()
    }

    private fun scheduleContentDrawCallbackIfReady() {
        if (released || contentDrawCallbackScheduled || adapter.itemCount == 0) return
        if (afterContentDrawn == null) return
        contentDrawCallbackScheduled = true
        recyclerView.doOnPreDraw {
            val callback = afterContentDrawn
            afterContentDrawn = null
            contentDrawCallbackScheduled = false
            recyclerView.postOnAnimation {
                if (!released) callback?.invoke()
            }
        }
    }

    private fun applyPendingScrollToTopIfReady() {
        val signal = pendingScrollToTopSignal ?: return
        if (signal != lastScrollToTopSignal || adapter.itemCount <= 0) return
        pendingScrollToTopSignal = null
        recyclerView.stopScroll()
        val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        manager.scrollToPositionWithOffset(0, 0)
        recyclerView.post {
            if (released || adapter.itemCount <= 0) return@post
            (recyclerView.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(0, 0)
        }
    }

    /**
     * Phase 2 entry point — the host screen pushes its current navigation playback
     * gate. Pagination is intentionally independent from emoji playback.
     */
    fun setScreenPlaybackGate(gate: EmojiPlaybackGate) {
        if (released) return
        recyclerView.setEmojiPlaybackGate(gate)
        if (gate == EmojiPlaybackGate.Inactive) {

        }
    }

    fun releaseViewResourcesForTrim() {
        releaseViewResources()
    }

    fun release() {
        releaseViewResources()
    }

    private fun releaseViewResources() {
        if (released) return
        released = true

        recyclerView.removeOnScrollListener(scrollListener)
        adapter.submitList(emptyList())
        recyclerView.adapter = null
        pendingScrollToTopSignal = null
        afterContentDrawn = null
        contentDrawCallbackScheduled = false
        onLoadMore = {}
        onPostOpen = {}
        onPostUpvote = {}
        onPostDownvote = {}
        onPostComment = {}
        onPostMenu = { _, _ -> }
        onAuthor = {}
        onPollVote = { _, _ -> }
    }

    override fun onDetachedFromWindow() {
        // Detach pauses any leftover skeleton ticker but does NOT touch the gate —
        // the host owns the gate via setScreenPlaybackGate.

        super.onDetachedFromWindow()
    }

    // Phase 2: do NOT auto-resume playback on attach. The gate is owned by the
    // navigation lifecycle and is restored by the host after the transition settles.

}

private class FeedHeaderAdapter(private val header: View) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun getItemCount() = 1
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        header.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return object : RecyclerView.ViewHolder(header) {}
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
}

private class NativeFeedScrollListener(
    private val adapter: NativePostAdapter,
    private val totalItemCount: () -> Int,
    private val hasMore: () -> Boolean,
    private val onLoadMore: () -> Unit,
    private val headerCount: Int,
) : RecyclerView.OnScrollListener() {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        if (dy == 0) return
        val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = (manager.findFirstVisibleItemPosition() - headerCount).coerceAtLeast(0)
        val last = manager.findLastVisibleItemPosition() - headerCount
        adapter.prefetchAvatars(recyclerView.context, first, last)
        if (dy > 0 && hasMore() && last >= totalItemCount() - LOAD_MORE_THRESHOLD) {
            onLoadMore()
        }
    }

    private companion object {
        const val LOAD_MORE_THRESHOLD = 5
    }
}

private class NativePostAdapter(
    private val onOpen: (Post) -> Unit,
    private val onUpvote: (Post) -> Unit,
    private val onDownvote: (Post) -> Unit,
    private val onComment: (Post) -> Unit,
    private val onMenu: (Post, Boolean) -> Unit,
    private val onAuthor: (String) -> Unit,
    private val onPollVote: (Post, String) -> Unit,
) : ListAdapter<Post, RecyclerView.ViewHolder>(PostDiff) {
    private var loadingMore = false
    private var insertAnimSignal: Int = 0
    private var insertAnimPostId: String? = null
    private var consumedInsertAnimSignal: Int = 0

    init {
        setHasStableIds(true)
    }

    val postCount: Int
        get() = currentList.size

    fun setInsertAnimation(signal: Int, postId: String?) {
        insertAnimSignal = signal
        insertAnimPostId = postId
    }

    fun setLoadingMore(value: Boolean) {
        if (loadingMore == value) return
        val footerPosition = currentList.size
        loadingMore = value
        if (value) {
            notifyItemInserted(footerPosition)
        } else {
            notifyItemRemoved(footerPosition)
        }
    }

    fun prefetchAvatars(context: Context, firstVisible: Int, lastVisible: Int) {
        if (firstVisible < 0 || lastVisible < firstVisible) return
        val from = (firstVisible - 2).coerceAtLeast(0)
        val to = (lastVisible + 8).coerceAtMost(currentList.lastIndex)
        if (from > to) return
        val sizePx = context.dp(96f).toInt()
        currentList.subList(from, to + 1).forEach { post ->
            CoilBitmapLoader.prefetch(
                context.applicationContext,
                buildAvatarUrl(post.userId, post.avatarConfig),
                sizePx,
            )
        }
    }

    override fun getItemCount(): Int = currentList.size + if (loadingMore) 1 else 0

    override fun getItemId(position: Int): Long = if (position < currentList.size) {
        getItem(position).id.hashCode().toLong()
    } else {
        Long.MIN_VALUE
    }

    override fun getItemViewType(position: Int): Int =
        if (position < currentList.size) VIEW_TYPE_POST else VIEW_TYPE_LOADING

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == VIEW_TYPE_LOADING) return LoadingMoreHolder(parent.context)
        prefetchNativeIcons(parent.context.applicationContext, parent.context.dp(48f).toInt())
        val view = NativePostView(parent.context)
        return NativePostHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder !is NativePostHolder) return
        val post = getItem(position)
        holder.view.bind(
            post = post,
            onOpen = onOpen,
            onUpvote = onUpvote,
            onDownvote = onDownvote,
            onComment = onComment,
            onMenu = onMenu,
            onAuthor = onAuthor,
            onPollVote = onPollVote,
        )
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (holder !is NativePostHolder) return
        val payload = payloads.lastOrNull() as? PostVotePayload
        if (payload != null) {
            holder.view.bindVote(payload.post)
            return
        }
        onBindViewHolder(holder, position)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is NativePostHolder) holder.view.recycle()
    }

    private companion object {
        const val VIEW_TYPE_POST = 0
        const val VIEW_TYPE_LOADING = 1
    }
}

private class NativePostHolder(val view: NativePostView) : RecyclerView.ViewHolder(view)

private class LoadingMoreHolder(context: Context) : RecyclerView.ViewHolder(
    FrameLayout(context).apply {
        layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            context.dp(52f).toInt(),
        )
        addView(
            ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
                indeterminateTintList = ColorStateList.valueOf(NativeColors.accent)
            },
            FrameLayout.LayoutParams(context.dp(24f).toInt(), context.dp(24f).toInt()).apply {
                gravity = Gravity.CENTER
            },
        )
    },
)

private object PostDiff : DiffUtil.ItemCallback<Post>() {
    override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean = oldItem == newItem

    override fun getChangePayload(oldItem: Post, newItem: Post): Any? {
        if (oldItem == newItem) return null
        val voteOnly = oldItem.copy(
            upvotes = newItem.upvotes,
            downvotes = newItem.downvotes,
            myVoteRaw = newItem.myVoteRaw,
            poll = newItem.poll,
        ) == newItem
        return if (voteOnly) PostVotePayload(newItem) else null
    }
}

private data class PostVotePayload(val post: Post)
