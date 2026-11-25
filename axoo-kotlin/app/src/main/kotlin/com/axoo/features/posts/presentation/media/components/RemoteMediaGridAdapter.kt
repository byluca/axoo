package com.axoo.features.posts.presentation.media.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Animatable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import android.view.Gravity
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Precision
import com.axoo.features.posts.domain.RemoteMedia
import com.axoo.features.posts.domain.RemoteMediaType
import com.axoo.ui.components.MediaPreviewOverlay
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp
import com.axoo.ui.theme.baloo2Typeface

// A local action occupies the first grid cell; it is never returned as an attachment.
internal val CreateStickerTile = RemoteMedia("create-sticker", "", "", 512, 512,
    "Crea sticker", RemoteMediaType.Sticker, provider = "local-action")

internal class RemoteMediaGridAdapter(
    context: Context,
    private val onMediaTap: (RemoteMedia) -> Unit,
    private val spacingPx: Int,
    private val onPreviewVisibilityChanged: (Boolean) -> Unit = {},
) : RecyclerView.Adapter<RemoteMediaGridAdapter.Holder>() {
    private var items: List<RemoteMedia> = emptyList()
    private var rows: List<JustifiedMediaRow> = emptyList()
    private val attachedRows = LinkedHashSet<RemoteMediaRowView>()
    private var contentWidthPx = 0
    private var previewVisible = false
    private val targetRowHeightPx = context.dp(TARGET_ROW_HEIGHT_DP)
    private val minimumItemWidthPx = context.dp(MIN_ITEM_WIDTH_DP)

    init {
        setHasStableIds(true)
    }

    fun submit(nextItems: List<RemoteMedia>) {
        if (items == nextItems) return
        items = nextItems
        rebuildRows()
    }

    fun setContentWidth(widthPx: Int) {
        if (widthPx <= 0 || contentWidthPx == widthPx) return
        contentWidthPx = widthPx
        rebuildRows()
    }

    override fun getItemId(position: Int): Long =
        rows[position].items.fold(17L) { result, item ->
            result * 31L + item.id.hashCode()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(
            RemoteMediaRowView(
                context = parent.context,
                spacingPx = spacingPx,
                onMediaTap = onMediaTap,
                onPreviewVisibilityChanged = { visible ->
                    setPreviewVisible(visible)
                    onPreviewVisibilityChanged(visible)
                },
            ),
        )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.view.bind(rows[position])
    }

    override fun onViewRecycled(holder: Holder) {
        holder.view.recycle()
        super.onViewRecycled(holder)
    }

    override fun onViewAttachedToWindow(holder: Holder) {
        super.onViewAttachedToWindow(holder)
        attachedRows += holder.view
        holder.view.setAnimationsRunning(!previewVisible)
    }

    override fun onViewDetachedFromWindow(holder: Holder) {
        attachedRows -= holder.view
        super.onViewDetachedFromWindow(holder)
    }

    override fun getItemCount(): Int = rows.size

    @SuppressLint("NotifyDataSetChanged")
    private fun rebuildRows() {
        if (contentWidthPx <= 0) return
        rows = buildJustifiedMediaRows(
            items = items,
            contentWidthPx = contentWidthPx,
            spacingPx = spacingPx,
            targetRowHeightPx = targetRowHeightPx,
            maximumItemsPerRow = (
                contentWidthPx / minimumItemWidthPx
                ).coerceIn(2, 6),
        )
        notifyDataSetChanged()
    }

    fun setPreviewVisible(visible: Boolean) {
        previewVisible = visible
        attachedRows.forEach { it.setAnimationsRunning(!visible) }
    }

    internal class Holder(val view: RemoteMediaRowView) : RecyclerView.ViewHolder(view)

    private companion object {
        const val TARGET_ROW_HEIGHT_DP = 112
        const val MIN_ITEM_WIDTH_DP = 88
    }
}

@SuppressLint("ViewConstructor")
internal class RemoteMediaRowView(
    context: Context,
    private val spacingPx: Int,
    private val onMediaTap: (RemoteMedia) -> Unit,
    private val onPreviewVisibilityChanged: (Boolean) -> Unit,
) : ViewGroup(context) {
    private var row: JustifiedMediaRow? = null
    private var shouldAnimate = true

    fun bind(nextRow: JustifiedMediaRow) {
        row = nextRow
        while (childCount < nextRow.items.size) {
            addView(
                RemoteMediaCellView(context, onMediaTap, onPreviewVisibilityChanged),
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
            )
        }
        while (childCount > nextRow.items.size) {
            (getChildAt(childCount - 1) as RemoteMediaCellView).recycle()
            removeViewAt(childCount - 1)
        }
        nextRow.items.forEachIndexed { index, item ->
            (getChildAt(index) as RemoteMediaCellView).bind(item)
        }
        setAnimationsRunning(shouldAnimate)
        requestLayout()
    }

    fun recycle() {
        for (index in 0 until childCount) {
            (getChildAt(index) as? RemoteMediaCellView)?.recycle()
        }
    }

    fun setAnimationsRunning(running: Boolean) {
        shouldAnimate = running
        for (index in 0 until childCount) {
            (getChildAt(index) as? RemoteMediaCellView)?.setAnimationsRunning(running)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val current = row
        if (current == null) {
            setMeasuredDimension(width, 0)
            return
        }
        current.widthsPx.forEachIndexed { index, childWidth ->
            getChildAt(index).measure(
                MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(current.heightPx, MeasureSpec.EXACTLY),
            )
        }
        setMeasuredDimension(width, current.heightPx + spacingPx)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val current = row ?: return
        var childLeft = 0
        current.widthsPx.forEachIndexed { index, childWidth ->
            getChildAt(index).layout(
                childLeft,
                0,
                childLeft + childWidth,
                current.heightPx,
            )
            childLeft += childWidth + spacingPx
        }
    }
}

@SuppressLint("ViewConstructor")
internal class RemoteMediaCellView(
    context: Context,
    private val onMediaTap: (RemoteMedia) -> Unit,
    private val onPreviewVisibilityChanged: (Boolean) -> Unit,
) : FrameLayout(context) {
    private var media: RemoteMedia? = null

    private val mediaView = com.axoo.features.posts.presentation.components.MediaImageView(context)
    private val createTile = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(NativeColors.surface)
            cornerRadius = context.dp(16).toFloat()
        }
        addView(ImageView(context).apply {
            imageTintList = ColorStateList.valueOf(NativeColors.textSecondary)
            scaleType = ImageView.ScaleType.FIT_CENTER
            load("file:///android_asset/icons/smiley-sticker.svg")
        }, LinearLayout.LayoutParams(context.dp(32), context.dp(32)))
        addView(TextView(context).apply {
            text = "Crea sticker"
            gravity = Gravity.CENTER
            typeface = context.baloo2Typeface(700)
            textSize = 14f
            setTextColor(NativeColors.textSecondary)
            includeFontPadding = false
        }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(8) })
        visibility = GONE
    }
    init {
        isClickable = true
        isFocusable = true
        setBackgroundColor(NativeColors.pickerSurface)
        setOnClickListener { media?.let(onMediaTap) }

        addView(mediaView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(createTile, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun bind(item: RemoteMedia) {
        if (media == item) return
        clearImages()
        media = item
        mediaView.cornerRadius = if (item.provider == "axoo") context.dp(12).toFloat() else 0f
        createTile.visibility = if (item == CreateStickerTile) VISIBLE else GONE
        contentDescription = item.title
        if (item == CreateStickerTile) { MediaPreviewOverlay.clear(this); return }
        MediaPreviewOverlay.bind(this, "${item.provider}-${item.id}",
            aspectRatio = item.width.toFloat() / item.height.coerceAtLeast(1),
            onVisibility = onPreviewVisibilityChanged) { previewContext ->
            ImageView(previewContext).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                // Reuse the decoded grid image when its drawable supports a copy.
                val decoded = mediaView.drawable?.constantState?.newDrawable(resources)?.mutate()
                if (decoded != null) {
                    setImageDrawable(decoded)
                    post { if (isAttachedToWindow) (drawable as? Animatable)?.start() }
                } else load(item.gridUrl()) {
                    size(600); precision(Precision.INEXACT); crossfade(false)
                }
            }
        }
        mediaView.scaleType = if (item.type == RemoteMediaType.Gif) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        mediaView.bind(item.gridUrl(), item.loadingPreviewUrl ?: item.previewFallbackUrl, maximumPixelSize = 240)
    }

    fun recycle() {
        MediaPreviewOverlay.clear(this)
        clearImages()
        media = null
    }

    fun setAnimationsRunning(running: Boolean) { mediaView.animationsRunning = running }
    private fun clearImages() { mediaView.clear() }

}

private fun RemoteMedia.gridUrl(): String = when (type) {
    RemoteMediaType.Meme -> previewUrl
    RemoteMediaType.Gif, RemoteMediaType.Sticker -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        previewUrl
    } else {
        previewFallbackUrl ?: previewUrl
    }
}
