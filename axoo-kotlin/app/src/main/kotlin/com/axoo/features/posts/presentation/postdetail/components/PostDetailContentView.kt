/*
 * Costruisce la lista interna della schermata Post Detail.
 *
 * Unisce la scheda del post, l'ordinamento e le schede dei commenti nello stesso
 * RecyclerView. Le singole schede arrivano da postcomponents.
 */
package com.axoo.features.posts.presentation.postdetail.components

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import com.axoo.engine.emoji.animation.drawable.*
import com.axoo.engine.emoji.animation.drawable.AnimatedLottieView
import com.axoo.engine.emoji.animation.model.*
import com.axoo.engine.emoji.animation.rendering.*
import com.axoo.engine.emoji.animation.time.*
import com.axoo.engine.emoji.image.CoilBitmapLoader
import com.axoo.engine.emoji.list.EmojiRecyclerView
import com.axoo.features.posts.domain.Comment
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.presentation.components.NativeCommentView
import com.axoo.features.posts.presentation.components.NativePostPresentation
import com.axoo.features.posts.presentation.components.NativePostView
import com.axoo.features.posts.presentation.components.dp
import com.axoo.features.posts.presentation.components.prefetchNativeIcons
import com.axoo.features.posts.presentation.postdetail.CommentUiModel
import com.axoo.features.posts.presentation.postdetail.FlattenedComment
import com.axoo.features.posts.presentation.postdetail.flattenComments
import com.axoo.features.posts.presentation.postdetail.hasSameRenderedBody
import com.axoo.ui.avatar.buildAvatarUrl
import com.axoo.ui.components.NativeSkeletonCardView
import com.axoo.ui.components.SkeletonType
import com.axoo.ui.theme.AppFontFamily
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.applyAxooTypography

private const val VIEW_TYPE_POST = 0
private const val VIEW_TYPE_COMMENTS_HEADER = 1
private const val VIEW_TYPE_COMMENT = 2
private const val VIEW_TYPE_COMMENT_SKELETON = 3
private const val VIEW_TYPE_POST_SKELETON = 4
private const val VIEW_TYPE_COMMENTS_EMPTY = 5
private const val EMOJI_RENDER_TAG = "EmojiTextRender"

internal fun commentAnchorOffset(
    viewportHeight: Int,
    paddingTop: Int,
    paddingBottom: Int,
    commentHeight: Int,
    bottomGap: Int,
): Int = (
        viewportHeight - paddingBottom - bottomGap - commentHeight - paddingTop
        ).coerceAtLeast(0)

internal class PostDetailContentView(context: Context) : FrameLayout(context) {
    private var onUpvote: (String) -> Unit = {}
    private var onDownvote: (String) -> Unit = {}
    private var onDelete: (String) -> Unit = {}
    private var onPostUpvote: () -> Unit = {}
    private var onPostDownvote: () -> Unit = {}
    private var onPollVote: (String) -> Unit = {}
    private var onMenu: (Comment) -> Unit = {}
    private var onReply: (Comment) -> Unit = {}
    private var onPostMenu: (Post) -> Unit = {}
    private var onAuthor: (String) -> Unit = {}
    private var released = false
    private var preserveViewportOnResize = false
    private var viewportAnchorPosition = RecyclerView.NO_POSITION
    private var viewportAnchorBottomGap = 0
    private var resizeAnchorPosition = RecyclerView.NO_POSITION
    private var resizeAnchorOffset = 0
    private val adapter = NativePostDetailAdapter(
        onUpvote = { onUpvote(it) },
        onDownvote = { onDownvote(it) },
        onDelete = { onDelete(it) },
        onPostUpvote = { onPostUpvote() },
        onPostDownvote = { onPostDownvote() },
        onPollVote = { onPollVote(it) },
        onMenu = { onMenu(it) },
        onReply = { onReply(it) },
        onPostMenu = { onPostMenu(it) },
        onAuthor = { onAuthor(it) },
    )

    private val contentLayoutManager = LinearLayoutManager(context)

    private val recyclerView = EmojiRecyclerView(context).apply {
        clipToPadding = false
        setAxooPadding(0, context.dp(4f).toInt(), 0, 0)
        layoutManager = contentLayoutManager
        adapter = this@PostDetailContentView.adapter
        itemAnimator = null
        addItemDecoration(CommentThreadDecoration(context))
    }

    init {
        setBackgroundColor(NativeColors.surface)
        recyclerView.setBackgroundColor(NativeColors.surface)
        prefetchNativeIcons(context.applicationContext, context.dp(48f).toInt())
        recyclerView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(
                    recyclerView: RecyclerView,
                    newState: Int,
                ) {
                    if (
                        preserveViewportOnResize &&
                        newState == RecyclerView.SCROLL_STATE_IDLE
                    ) {
                        captureViewportAnchor()
                    }
                }
            },
        )
        addView(
            recyclerView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (
            !preserveViewportOnResize ||
            oldHeight <= 0 ||
            height == oldHeight
        ) {
            return
        }

        val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        if (manager.findViewByPosition(viewportAnchorPosition) == null) {
            captureViewportAnchor()
        }
        val position = viewportAnchorPosition
        val anchor = manager.findViewByPosition(position) ?: return

        resizeAnchorPosition = position
        resizeAnchorOffset = commentAnchorOffset(
            viewportHeight = height,
            paddingTop = recyclerView.paddingTop,
            paddingBottom = recyclerView.paddingBottom,
            commentHeight = manager.getDecoratedMeasuredHeight(anchor),
            bottomGap = viewportAnchorBottomGap,
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (resizeAnchorPosition != RecyclerView.NO_POSITION) {
            val position = resizeAnchorPosition
            val offset = resizeAnchorOffset
            resizeAnchorPosition = RecyclerView.NO_POSITION
            if (position < adapter.itemCount) {
                (recyclerView.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(position, offset)
            }
        }
        super.onLayout(changed, left, top, right, bottom)
    }

    fun prepareForInputTransition() {
        if (released) return
        preserveViewportOnResize = true
        recyclerView.stopScroll()
        captureViewportAnchor()
    }

    fun canScrollUp(): Boolean = recyclerView.canScrollVertically(-1)

    fun setComposerBottomPadding(height: Int) {
        if (recyclerView.paddingBottom == height) return
        recyclerView.setAxooPadding(
            recyclerView.paddingLeft,
            recyclerView.paddingTop,
            recyclerView.paddingRight,
            height,
        )
    }

    fun setCallbacks(
        onUpvote: (String) -> Unit,
        onDownvote: (String) -> Unit,
        onDelete: (String) -> Unit,
        onPostUpvote: () -> Unit,
        onPostDownvote: () -> Unit,
        onMenu: (Comment) -> Unit,
        onReply: (Comment) -> Unit,
        onPostMenu: (Post) -> Unit,
        onAuthor: (String) -> Unit = {},
        onPollVote: (String) -> Unit = {},
    ) {
        this.onUpvote = onUpvote
        this.onDownvote = onDownvote
        this.onDelete = onDelete
        this.onPostUpvote = onPostUpvote
        this.onPostDownvote = onPostDownvote
        this.onPollVote = onPollVote
        this.onMenu = onMenu
        this.onReply = onReply
        this.onPostMenu = onPostMenu
        this.onAuthor = onAuthor
    }

    fun render(
        post: Post?,
        comments: List<CommentUiModel>,
        isCommentsLoading: Boolean,
        isCommentsError: Boolean,
    ) {
        if (released) return
        setBackgroundColor(NativeColors.surface)
        recyclerView.setBackgroundColor(NativeColors.surface)
        adapter.submitPayload(
            post = post,
            comments = comments,
            isCommentsLoading = isCommentsLoading,
            isCommentsError = isCommentsError,
        )
    }

    fun scrollToComment(id: String) {
        val position = adapter.positionForComment(id) ?: return
        recyclerView.post {
            if (!released) (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, context.dp(12f).toInt())
        }
    }

    fun scrollToBottom() {
        if (released) return
        preserveViewportOnResize = false
        viewportAnchorPosition = RecyclerView.NO_POSITION
        resizeAnchorPosition = RecyclerView.NO_POSITION
        recyclerView.post {
            if (!released && adapter.itemCount > 0) {
                recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    /**
     * Phase 2 entry point — pushes the navigation playback gate to the underlying
     * RecyclerView. See [com.axoo.engine.emoji.list.EmojiPlaybackGate].
     */
    fun setScreenPlaybackGate(gate: com.axoo.engine.emoji.list.EmojiPlaybackGate) {
        if (released) return
        recyclerView.setEmojiPlaybackGate(gate)
    }

    fun release() {
        if (released) return
        released = true
        preserveViewportOnResize = false
        viewportAnchorPosition = RecyclerView.NO_POSITION
        resizeAnchorPosition = RecyclerView.NO_POSITION
        adapter.clear()
        recyclerView.adapter = null
        onUpvote = {}
        onDownvote = {}
        onDelete = {}
        onPostUpvote = {}
        onPostDownvote = {}
        onPollVote = {}
        onMenu = {}
        onReply = {}
        onPostMenu = {}
        onAuthor = {}
    }

    override fun onDetachedFromWindow() {
        // Phase 2: the gate is owned by the navigation lifecycle, not by attach state.
        super.onDetachedFromWindow()
    }

    // Phase 2: do NOT auto-resume on attach.

    private fun captureViewportAnchor() {
        val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val position = manager.findLastVisibleItemPosition()
        val anchor = manager.findViewByPosition(position) ?: return
        val viewportHeight = recyclerView.height.takeIf { it > 0 } ?: return
        viewportAnchorPosition = position
        viewportAnchorBottomGap = (
                viewportHeight - recyclerView.paddingBottom -
                        manager.getDecoratedBottom(anchor)
                )
    }

}

private data class DetailPayload(
    val post: Post?,
    val flattenedComments: List<FlattenedComment>,
    val isCommentsLoading: Boolean,
    val isCommentsError: Boolean,
)

private val DetailPayload.showsEmptyComments: Boolean
    get() = !isCommentsLoading && !isCommentsError && flattenedComments.isEmpty()

private class NativePostDetailAdapter(
    private val onUpvote: (String) -> Unit,
    private val onDownvote: (String) -> Unit,
    private val onDelete: (String) -> Unit,
    private val onPostUpvote: () -> Unit,
    private val onPostDownvote: () -> Unit,
    private val onPollVote: (String) -> Unit,
    private val onMenu: (Comment) -> Unit,
    private val onReply: (Comment) -> Unit,
    private val onPostMenu: (Post) -> Unit,
    private val onAuthor: (String) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var payload = DetailPayload(
        post = null,
        flattenedComments = emptyList(),
        isCommentsLoading = false,
        isCommentsError = false,
    )

    private val commentCallback = CommentDiff()


    fun submitPayload(
        post: Post?,
        comments: List<CommentUiModel>,
        isCommentsLoading: Boolean,
        isCommentsError: Boolean,
    ) {
        val flat = if (isCommentsLoading || isCommentsError) {
            emptyList()
        } else {
            flattenComments(comments)
        }

        val newPayload = DetailPayload(
            post = post,
            flattenedComments = flat,
            isCommentsLoading = isCommentsLoading,
            isCommentsError = isCommentsError,
        )

        val oldPayload = payload
        payload = newPayload

        val postChanged = oldPayload.post != newPayload.post
        val loadingChanged =
            oldPayload.isCommentsLoading != newPayload.isCommentsLoading ||
                    oldPayload.isCommentsError != newPayload.isCommentsError

        val commentsChanged = oldPayload.flattenedComments != newPayload.flattenedComments
        val emptyStateChanged = oldPayload.showsEmptyComments != newPayload.showsEmptyComments

        if (postChanged) {
            val oldPost = oldPayload.post
            val newPost = newPayload.post
            if (oldPost != null && newPost != null && oldPost.isVoteOnlyChange(newPost)) {
                notifyItemChanged(0, PostVotePayload(newPost))
            } else {
                notifyItemChanged(0)
            }
        }
        if (loadingChanged || emptyStateChanged) {
            notifyDataSetChanged()
            return
        }

        if (commentsChanged) {
            commentCallback.calculate(
                oldList = oldPayload.flattenedComments,
                newList = newPayload.flattenedComments,
            ).dispatchUpdatesTo(CommentListUpdateCallback(this))
        }
    }

    fun positionForComment(id: String): Int? = payload.flattenedComments.indexOfFirst { it.comment.id == id }
        .takeIf { it >= 0 }?.plus(2)

    fun clear() {
        submitPayload(
            post = null,
            comments = emptyList(),
            isCommentsLoading = false,
            isCommentsError = false,
        )
    }

    override fun getItemCount(): Int {
        val base = 2
        if (payload.isCommentsLoading) return base + 4
        if (payload.isCommentsError) return base
        if (payload.showsEmptyComments) return base + 1
        return base + payload.flattenedComments.size
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> if (payload.post == null) VIEW_TYPE_POST_SKELETON else VIEW_TYPE_POST
            1 -> VIEW_TYPE_COMMENTS_HEADER
            else -> when {
                payload.isCommentsLoading -> VIEW_TYPE_COMMENT_SKELETON
                payload.showsEmptyComments -> VIEW_TYPE_COMMENTS_EMPTY
                else -> VIEW_TYPE_COMMENT
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val context = parent.context

        return when (viewType) {
            VIEW_TYPE_POST -> {
                prefetchNativeIcons(context.applicationContext, context.dp(48f).toInt())

                val view = NativePostView(
                    context = context,
                    presentation = NativePostPresentation.Detail,
                )

                PostViewHolder(view)
            }

            VIEW_TYPE_POST_SKELETON -> {
                SkeletonViewHolder(NativeSkeletonCardView(context, rows = 1).apply {
                })
            }

            VIEW_TYPE_COMMENTS_HEADER -> {
                CommentsHeaderViewHolder(context)
            }

            VIEW_TYPE_COMMENT -> {
                prefetchNativeIcons(context.applicationContext, context.dp(48f).toInt())

                val view = NativeCommentView(context)

                CommentViewHolder(view)
            }

            VIEW_TYPE_COMMENT_SKELETON -> {
                SkeletonViewHolder(NativeSkeletonCardView(context, rows = 1, type = SkeletonType.Comment).apply {
                })
            }

            VIEW_TYPE_COMMENTS_EMPTY -> {
                EmptyCommentsViewHolder(NativeCommentsEmptyView(context).apply {
                })
            }

            else -> throw IllegalArgumentException("Unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (holder) {
            is PostViewHolder -> {
                val post = payload.post ?: return

                holder.view.bind(
                    post = post,
                    onOpen = {},
                    onUpvote = { onPostUpvote() },
                    onDownvote = { onPostDownvote() },
                    onPollVote = { _, option -> onPollVote(option) },
                    onComment = {},
                    onAuthor = onAuthor,
                    onMenu = { targetPost, _ ->
                        onPostMenu(targetPost)
                    },
                )
            }

            is CommentViewHolder -> {
                val idx = position - 2
                val flat = payload.flattenedComments.getOrNull(idx) ?: return

                holder.view.bind(
                    item = flat.item,
                    thread = flat,
                    onUpvote = { onUpvote(it.id) },
                    onDownvote = { onDownvote(it.id) },
                    onDelete = { onDelete(it.id) },
                    onMenu = onMenu,
                    onReply = onReply,
                    onAuthor = onAuthor,
                )
            }

            is SkeletonViewHolder -> {
            }

            is EmptyCommentsViewHolder -> {
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        when (val payload = payloads.lastOrNull()) {
            is PostVotePayload -> {
                (holder as? PostViewHolder)?.view?.bindVote(payload.post)
                return
            }

            is CommentVotePayload -> {
                (holder as? CommentViewHolder)?.view?.bindVote(payload.item)
                return
            }

            is CommentMetadataPayload -> {
                (holder as? CommentViewHolder)?.view?.bindMetadata(payload.item)
                return
            }
        }
        onBindViewHolder(holder, position)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is PostViewHolder -> holder.view.recycle()
            is CommentViewHolder -> holder.view.recycle()
            else -> Unit
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        recyclerView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(
                    rv: RecyclerView,
                    newState: Int,
                ) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        val manager = rv.layoutManager as? LinearLayoutManager ?: return
                        val first = manager.findFirstVisibleItemPosition()
                        val last = manager.findLastVisibleItemPosition()
                        prefetchVisibleAvatars(rv.context, first, last)
                    }
                }
            },
        )
    }

    private fun prefetchVisibleAvatars(
        context: Context,
        first: Int,
        last: Int,
    ) {
        if (first < 0 || last < first) return

        for (i in first..last) {
            val flat = payload.flattenedComments.getOrNull(i - 2) ?: continue
            if (flat.comment.isDeleted) continue
            val url = buildAvatarUrl(flat.comment.userId, flat.comment.avatarConfig)

            val sizePx = context.dp(84f).toInt()
            if (CoilBitmapLoader.peek(context.applicationContext, url, sizePx) == null) {
                CoilBitmapLoader.prefetch(context.applicationContext, url, sizePx)
            }
        }
    }
}

private class PostViewHolder(
    val view: NativePostView,
) : RecyclerView.ViewHolder(view)

private class CommentViewHolder(
    val view: NativeCommentView,
) : RecyclerView.ViewHolder(view)

private class SkeletonViewHolder(
    val view: NativeSkeletonCardView,
) : RecyclerView.ViewHolder(view)

private class EmptyCommentsViewHolder(
    val view: NativeCommentsEmptyView,
) : RecyclerView.ViewHolder(view)

private class NativeCommentsEmptyView(context: Context) : LinearLayout(context) {
    private val label = TextView(context).apply {
        text = "Scrivi il primo commento..."
        gravity = Gravity.CENTER
        textSize = 20f
        includeFontPadding = false
        applyAxooTypography(
            family = AppFontFamily.BALOO2,
            weight = 700,
        )
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(
            context.dp(24f).toInt(),
            context.dp(22f).toInt(),
            context.dp(24f).toInt(),
            context.dp(42f).toInt(),
        )
        layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        val illustrationSize = context.dp(196f).toInt()
        addView(
            AnimatedLottieView(context, illustrationSize).apply {
                setAnimation(
                    id = "comments-empty-popcorn",
                    path = "axooji/ui/popcorn-icon.tgs",
                )
            },
            LayoutParams(illustrationSize, illustrationSize),
        )

        addView(
            label,
            LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = context.dp(10f).toInt()
            },
        )
    }

}

private class CommentsHeaderViewHolder(context: Context) : RecyclerView.ViewHolder(
    PostDetailCommentsHeaderView(context).apply {
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    },
)

internal class CommentDiff {
    fun calculate(
        oldList: List<FlattenedComment>,
        newList: List<FlattenedComment>,
    ): DiffUtil.DiffResult = DiffUtil.calculateDiff(
        object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldList.size

            override fun getNewListSize(): Int = newList.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldList[oldItemPosition].item.stableKey == newList[newItemPosition].item.stableKey

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldList[oldItemPosition] == newList[newItemPosition]

            override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
                val oldRow = oldList[oldItemPosition]
                val newRow = newList[newItemPosition]
                if (oldRow.copy(item = newRow.item) != newRow) return null
                val oldItem = oldList[oldItemPosition].item
                val newItem = newList[newItemPosition].item
                return when {
                    oldItem.isVoteOnlyChange(newItem) -> CommentVotePayload(newItem)
                    oldItem.hasSameRenderedBody(newItem) -> CommentMetadataPayload(newItem)
                    else -> null
                }
            }
        },
    )
}

private data class PostVotePayload(val post: Post)

private data class CommentVotePayload(val item: CommentUiModel)

private data class CommentMetadataPayload(val item: CommentUiModel)

private fun Post.isVoteOnlyChange(other: Post): Boolean {
    if (id != other.id || this == other) return false
    return copy(
        upvotes = other.upvotes,
        downvotes = other.downvotes,
        myVoteRaw = other.myVoteRaw,
        poll = other.poll,
    ) == other
}

private fun CommentUiModel.isVoteOnlyChange(other: CommentUiModel): Boolean {
    if (stableKey != other.stableKey || this == other) return false
    return copy(
        comment = comment.copy(
            upvotes = other.comment.upvotes,
            downvotes = other.comment.downvotes,
            myVoteRaw = other.comment.myVoteRaw,
        ),
    ) == other
}

private class CommentListUpdateCallback(
    private val adapter: RecyclerView.Adapter<*>,
) : ListUpdateCallback {
    override fun onInserted(position: Int, count: Int) {
        adapter.notifyItemRangeInserted(position + 2, count)
    }

    override fun onRemoved(position: Int, count: Int) {
        adapter.notifyItemRangeRemoved(position + 2, count)
    }

    override fun onMoved(fromPosition: Int, toPosition: Int) {
        adapter.notifyItemMoved(fromPosition + 2, toPosition + 2)
    }

    override fun onChanged(position: Int, count: Int, payload: Any?) {
        adapter.notifyItemRangeChanged(position + 2, count, payload)
    }
}
