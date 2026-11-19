package com.axoo.engine.emoji.picker

import com.axoo.ui.components.MediaPreviewOverlay
import com.axoo.engine.emoji.animation.drawable.AnimatedLottieView
import com.axoo.ui.theme.NativeColors
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.axoo.engine.emoji.animation.drawable.AnimatedEmojiLease
import com.axoo.engine.emoji.animation.drawable.AnimatedEmojiRegistry
import com.axoo.engine.emoji.animation.model.AnimatedEmojiKey
import com.axoo.engine.emoji.animation.model.EmojiRasterSizePolicy
import com.axoo.engine.emoji.animation.time.PlaybackPolicy
import com.axoo.engine.emoji.catalog.Emoji
import com.axoo.engine.emoji.list.EmojiFrameCoordinator
import com.axoo.engine.emoji.list.EmojiPlaybackController
import com.axoo.engine.emoji.list.EmojiRecyclerView
import com.axoo.ui.theme.baloo2Typeface

internal enum class CellState { LOADING, READY, PLAYING }

internal class EmojiAdapter(
    private val rows: List<EmojiPickerRow>,
    private val cellSizePx: Int,
    private val appContext: Context,
    private val onEmojiClick: (Emoji) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
    EmojiPlaybackController {
    companion object {
        const val TYPE_EMOJI = 0
        const val TYPE_HEADER = 1

    }

    private var previewVisible = false
    private val playingHolders = LinkedHashSet<EmojiViewHolder>()

    private val density = appContext.resources.displayMetrics.density
    private val bitmapSize = EmojiRasterSizePolicy.pickerSizePx(density)
    private var attachedRecyclerView: RecyclerView? = null
    private var sharedFrameCoordinator: EmojiFrameCoordinator? = null
    private var sharedSurfaceCoordinator: EmojiPickerPlaybackCoordinator? = null
    private var nextBindToken = 0L

    /**
     * Raccoglie le celle visibili per la selezione delle animazioni.
     */
    fun collectPlaybackCandidates(): List<PlaybackPolicy.Candidate<EmojiViewHolder>> {
        if (previewVisible) return emptyList()
        val rv = attachedRecyclerView ?: return emptyList()
        if (!rv.isAttachedToWindow) return emptyList()
        val childCount = rv.childCount
        if (childCount == 0) return emptyList()
        // Viewport rect computed once: local recycler coordinates are enough,
        // getGlobalVisibleRect() would be more costly and is not needed here.
        val viewportLeft = rv.paddingLeft
        val viewportTop = rv.paddingTop
        val viewportRight = rv.width - rv.paddingRight
        val viewportBottom = playbackViewportBottom(rv)
        val out = ArrayList<PlaybackPolicy.Candidate<EmojiViewHolder>>(childCount)
        for (i in 0 until childCount) {
            val child = rv.getChildAt(i) ?: continue
            if (!isActuallyVisible(child, rv)) continue
            val holder = rv.getChildViewHolder(child) as? EmojiViewHolder ?: continue
            val position = holder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) continue
            val ready = (holder.state == CellState.READY || holder.state == CellState.PLAYING) &&
                holder.hasPreparedDrawable()

            // Pixel-aware visibility: clip the cell rect against the viewport.
            // Int-only math, no temporary Rect per child (see spec perf note).
            val visibleLeft = maxOf(child.left, viewportLeft)
            val visibleTop = maxOf(child.top, viewportTop)
            val visibleRight = minOf(child.right, viewportRight)
            val visibleBottom = minOf(child.bottom, viewportBottom)
            val visibleWidth = (visibleRight - visibleLeft).coerceAtLeast(0)
            val visibleHeight = (visibleBottom - visibleTop).coerceAtLeast(0)
            val visibleAreaPx = visibleWidth * visibleHeight
            val visibleCenterY = if (visibleAreaPx > 0) {
                (visibleTop + visibleBottom) / 2f
            } else {
                child.top + child.height / 2f
            }
            // Per-mille of the full cell so the priority is density-independent
            // and quantizes cleanly into signature buckets.
            val fullArea = child.width.coerceAtLeast(1).toLong() *
                child.height.coerceAtLeast(1).toLong()
            val visiblePermille =
                (visibleAreaPx.toLong() * 1000L / fullArea).toInt().coerceIn(0, 1000)
            out += PlaybackPolicy.Candidate(
                id = holder.bindToken,
                value = holder,
                stableOrder = position,
                ready = ready,
                top = child.top,
                left = child.left,
                visibleAreaPx = visibleAreaPx,
                visibleCenterY = visibleCenterY,
                visiblePermille = visiblePermille,
            )
        }
        return out
    }

    fun startCandidate(candidate: PlaybackPolicy.Candidate<EmojiViewHolder>): Boolean {
        val holder = candidate.value
        // La cella può essere stata riassociata dopo la raccolta dei candidati.
        if (holder.bindToken != candidate.id) return false
        if (holder.state != CellState.READY) return false
        if (!holder.hasPreparedDrawable()) return false
        return startPlaying(holder)
    }

    fun pauseExcept(activeBindTokens: Set<Long>): Int {
        if (playingHolders.isEmpty()) return 0
        val victims = playingHolders.filter { it.bindToken !in activeBindTokens }
        for (holder in victims) pauseHolderPlaying(holder)
        return victims.size
    }

    inner class EmojiViewHolder(val cellView: EmojiCellView, val bitmapPx: Int) :
        RecyclerView.ViewHolder(cellView) {

        var state: CellState = CellState.LOADING
        var boundName: String? = null
        var bindToken: Long = 0L
        private var emojiLease: AnimatedEmojiLease? = null

        fun bindAnimatedEmoji(lease: AnimatedEmojiLease?) {
            val previous = emojiLease
            if (previous === lease) return
            cellView.setAnimatedEmojiDrawable(null, requestInvalidate = false)
            previous?.close()
            emojiLease = lease
            cellView.setAnimatedEmojiDrawable(lease?.drawable, requestInvalidate = true)
        }

        fun playPrepared(): Boolean {
            val lease = emojiLease ?: return false
            lease.play()
            return true
        }
        fun pausePrepared() {
            emojiLease?.pause()
        }
        fun clearPrepared() {
            bindAnimatedEmoji(null)
        }
        fun hasPreparedDrawable(): Boolean = emojiLease != null
    }

    inner class HeaderViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    init {
        setHasStableIds(true)
    }

    override fun getItemViewType(position: Int): Int = when (val row = rows[position]) {
        is EmojiPickerRow.Header -> TYPE_HEADER
        is EmojiPickerRow.Item -> TYPE_EMOJI
    }

    override fun getItemId(position: Int): Long {
        return when (val row = rows[position]) {
            is EmojiPickerRow.Item -> ((row.category.hashCode().toLong() and 0x7FFFFFFFL) shl 32) xor row.emoji.id.toLong()
            is EmojiPickerRow.Header ->
                Long.MIN_VALUE or (row.title.hashCode().toLong() and 0xFFFFFFFFL)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == TYPE_HEADER) {
            val density = parent.context.resources.displayMetrics.density
            val textView = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (40 * density).toInt()
                )
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    (12 * density).toInt(),
                    (6 * density).toInt(),
                    (12 * density).toInt(),
                    (10 * density).toInt()
                )
                textSize = 14f
                setTextColor(NativeColors.textSecondary)
                typeface = context.baloo2Typeface(800)
            }
            return HeaderViewHolder(textView)
        }

        val bitmapPx = bitmapSize
        val cellPx = cellSizePx

        val cellView = EmojiCellView(parent.context, bitmapPx).apply {
            layoutParams = RecyclerView.LayoutParams(cellPx, cellPx)
            setFrameCoordinator(sharedFrameCoordinator)
        }

        return EmojiViewHolder(cellView, bitmapPx)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is EmojiPickerRow.Header -> {
                (holder as HeaderViewHolder).textView.text = row.title
                return
            }
            is EmojiPickerRow.Item -> bindEmoji(holder as EmojiViewHolder, row.emoji)
        }
    }

    private fun bindEmoji(holder: EmojiViewHolder, emoji: Emoji) {
        clearHolderState(holder)

        holder.boundName = emoji.name
        holder.bindToken = ++nextBindToken

        holder.cellView.setFrameCoordinator(sharedFrameCoordinator)
        holder.cellView.setOnClickListener {
            onEmojiClick(emoji)
        }

        MediaPreviewOverlay.bind(holder.cellView, "emoji-${emoji.id}", maximumSizeDp = 220, onVisibility = { shown ->
            previewVisible = shown
            if (shown) pauseExcept(emptySet())
            sharedSurfaceCoordinator?.markDirty()
        }) { context ->
            AnimatedLottieView(context, (188 * context.resources.displayMetrics.density).toInt()).apply {
                setAnimation(emoji.name, emoji.assetPath)
            }
        }
        bindAnimatedEmoji(holder, emoji)
    }

    private fun bindAnimatedEmoji(
        holder: EmojiViewHolder,
        emoji: Emoji,
    ) {
        val lease = AnimatedEmojiRegistry.acquire(
            context = appContext,
            key = AnimatedEmojiKey(
                id = emoji.name,
                path = emoji.assetPath,
                sizePx = holder.bitmapPx.coerceAtLeast(1),
            ),
            host = holder.cellView,
        )
        lease.pause()
        holder.bindAnimatedEmoji(lease)
        holder.state = CellState.READY
        notifyCellReady()
    }

    /**
     * Rilascia la lease e invalida la cella che può restare visibile.
     */
    private fun clearHolder(holder: EmojiViewHolder) {
        clearHolderState(holder)
        holder.cellView.clearContent(requestInvalidate = true)
    }

    /**
     * Rilascia la lease e azzera il binding della cella da riassociare o riciclare.
     */
    private fun clearHolderState(holder: EmojiViewHolder) {
        MediaPreviewOverlay.clear(holder.cellView)
        holder.bindToken = ++nextBindToken

        pauseHolderPlaying(holder)

        holder.clearPrepared()

        holder.state = CellState.LOADING
    }

    private fun startPlaying(holder: EmojiViewHolder): Boolean {
        holder.state = CellState.PLAYING
        playingHolders.add(holder)
        if (!holder.playPrepared()) {
            holder.state = CellState.READY
            playingHolders.remove(holder)
            return false
        }
        return true
    }

    private fun pauseHolderPlaying(holder: EmojiViewHolder) {
        if (holder.state != CellState.PLAYING) return
        holder.pausePrepared()
        holder.state = CellState.READY
        playingHolders.remove(holder)
    }

    private fun playbackViewportBottom(recyclerView: RecyclerView): Int =
        // With clipToPadding=false, bottom padding is scroll clearance for the
        // delete button; cells drawn there are still visible and can animate.
        if (recyclerView.clipToPadding) recyclerView.height - recyclerView.paddingBottom else recyclerView.height

    private fun isActuallyVisible(child: View, recyclerView: RecyclerView): Boolean {
        return child.bottom > recyclerView.paddingTop &&
            child.top < playbackViewportBottom(recyclerView) &&
            child.right > recyclerView.paddingLeft &&
            child.left < recyclerView.width - recyclerView.paddingRight
    }

    /**
     * Rilascia le lease delle celle visibili quando il picker viene nascosto senza detach.
     * Conserva boundName per ricaricarle alla riapertura.
     */
    fun pauseAndReleaseAllVisible(recyclerView: RecyclerView) {
        for (i in 0 until recyclerView.childCount) {
            val holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i))
            if (holder is EmojiViewHolder) {
                clearHolder(holder)
            }
        }
        playingHolders.clear()
    }

    /**
     * Riassocia le celle visibili le cui lease sono state rilasciate alla chiusura.
     */
    private fun resumeVisible(recyclerView: RecyclerView) {
        for (i in 0 until recyclerView.childCount) {
            val holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i))
            if (holder is EmojiViewHolder &&
                holder.state == CellState.LOADING &&
                !holder.hasPreparedDrawable() &&
                holder.boundName != null
            ) {
                safeNotifyItemChanged(recyclerView, holder)
            }
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder !is EmojiViewHolder) return
        if (holder.state == CellState.READY) {
            notifyCellReady()
            return
        }
        // A bound cell whose lease was released while hidden needs a rebind.
        val needsReload = holder.state == CellState.LOADING &&
            !holder.hasPreparedDrawable() &&
            holder.boundName != null
        if (needsReload && pickerPlaybackAllowed()) {
            safeNotifyItemChanged(holder.itemView.parent as? RecyclerView, holder)
        }
    }

    private fun safeNotifyItemChanged(recyclerView: RecyclerView?, holder: EmojiViewHolder) {
        recyclerView ?: return
        recyclerView.post {
            if (attachedRecyclerView !== recyclerView) return@post
            if (recyclerView.adapter !== this) return@post
            if (holder.boundName == null) return@post
            if (!holder.itemView.isAttachedToWindow) return@post
            if (holder.itemView.parent !== recyclerView) return@post
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                notifyItemChanged(pos)
            }
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (holder is EmojiViewHolder) {
            // Il detach durante lo scroll mette in pausa; il riciclo rilascia la lease.
            pauseHolderPlaying(holder)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is EmojiViewHolder) {
            // Recycled views are detached: use the state-only teardown
            // (no invalidate request) and reset extra adapter fields.
            clearHolderState(holder)
            holder.boundName = null
            holder.cellView.setOnClickListener(null)
        }
    }

    override fun onFailedToRecycleView(holder: RecyclerView.ViewHolder): Boolean {
        if (holder is EmojiViewHolder) {
            clearHolderState(holder)
            holder.boundName = null
            holder.cellView.setOnClickListener(null)
            return true
        }
        return super.onFailedToRecycleView(holder)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
        // Raggruppa gli invalidate per frame; gli altri recycler usano invalidate diretto.
        sharedFrameCoordinator = (recyclerView as? EmojiRecyclerView)?.frameCoordinator
        sharedSurfaceCoordinator = (recyclerView as? EmojiRecyclerView)?.surfaceCoordinatorOrNull
        sharedSurfaceCoordinator?.bindAdapter(this)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        sharedSurfaceCoordinator?.bindAdapter(null)
        if (attachedRecyclerView === recyclerView) {
            attachedRecyclerView = null
            sharedFrameCoordinator = null
            sharedSurfaceCoordinator = null
        }
        for (i in 0 until recyclerView.childCount) {
            val holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i))
            if (holder is EmojiViewHolder) {
                // Recycler unmounting — cells are about to leave the
                // window; state-only teardown avoids the invalidate storm.
                clearHolderState(holder)
            }
        }
        playingHolders.clear()
    }

    override fun getItemCount() = rows.size

    override fun onEmojiViewportChanged(recyclerView: EmojiRecyclerView) {
        if (recyclerView.playbackAllowed) {
            resumeVisible(recyclerView)
            sharedSurfaceCoordinator?.markDirty()
        }
    }

    private fun pickerPlaybackAllowed(): Boolean =
        (attachedRecyclerView as? EmojiRecyclerView)?.playbackAllowed == true

    private fun notifyCellReady() {
        if (pickerPlaybackAllowed()) {
            sharedSurfaceCoordinator?.markDirty()
        }
    }
}
