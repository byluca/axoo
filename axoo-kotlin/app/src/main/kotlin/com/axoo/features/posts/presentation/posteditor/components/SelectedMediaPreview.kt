package com.axoo.features.posts.presentation.posteditor.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import coil.dispose
import coil.load
import coil.size.Precision
import com.axoo.ui.components.applyNativePillSurface
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp
import kotlin.math.roundToInt

data class MediaItem(
    val url: String,
    val fallbackUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)

internal data class MediaPreviewViewport(val width: Int, val height: Int)

internal fun mediaPreviewViewport(
    maxSize: Int,
    mediaWidth: Int,
    mediaHeight: Int,
): MediaPreviewViewport {
    if (maxSize <= 0) return MediaPreviewViewport(0, 0)
    if (mediaWidth <= 0 || mediaHeight <= 0) return MediaPreviewViewport(maxSize, maxSize)

    val minimumEdge = (maxSize * MIN_PREVIEW_EDGE_RATIO).roundToInt()
    return if (mediaWidth >= mediaHeight) {
        MediaPreviewViewport(
            width = maxSize,
            height = (maxSize * mediaHeight.toFloat() / mediaWidth)
                .roundToInt()
                .coerceIn(minimumEdge, maxSize),
        )
    } else {
        MediaPreviewViewport(
            width = (maxSize * mediaWidth.toFloat() / mediaHeight)
                .roundToInt()
                .coerceIn(minimumEdge, maxSize),
            height = maxSize,
        )
    }
}

@SuppressLint("ViewConstructor")
internal class NativeSelectedMediaPreviewView(context: Context) : FrameLayout(context) {
    private val image = ImageView(context).apply {
        background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = context.dp(PostEditorStyle.mediaRadius)
        }
        clipToOutline = true
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
    private val status = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 12f
        typeface = context.baloo2Typeface(700)
        setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
        visibility = View.GONE
    }
    private val removeButton = ImageButton(context).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(context.dp(9), context.dp(9), context.dp(9), context.dp(9))
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x99000000.toInt())
        }
        load("file:///android_asset/icons/close.svg") {
            precision(Precision.INEXACT)
            crossfade(false)
        }
    }

    private var item: MediaItem? = null
    private var modelKey: String? = null
    private var previewLoadFailed = false
    private var onRemove: () -> Unit = {}

    init {
        visibility = View.GONE
        applyNativePillSurface(cornerRadiusDp = PostEditorStyle.mediaRadius)

        addView(
            image,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        addView(
            status,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        addView(
            removeButton,
            LayoutParams(context.dp(36), context.dp(36), Gravity.TOP or Gravity.END).apply {
                topMargin = context.dp(8)
                marginEnd = context.dp(8)
            },
        )

        removeButton.setOnClickListener {
            onRemove()
        }
    }

    fun setOnRemove(callback: () -> Unit) {
        onRemove = callback
    }

    fun bind(nextItem: MediaItem?) {
        item = nextItem
        isVisible = nextItem != null
        if (nextItem == null) {
            clearImage()
            status.visibility = View.GONE
            return
        }

        val model = nextItem.previewModel()
        val nextKey = model.toString()
        if (modelKey != nextKey) {
            modelKey = nextKey
            previewLoadFailed = false
            image.load(model) {
                precision(Precision.INEXACT)
                crossfade(false)
                listener(
                    onSuccess = { _, _ ->
                        if (modelKey == nextKey) {
                            previewLoadFailed = false
                            updateStatus()
                        }
                    },
                    onError = { _, _ ->
                        if (modelKey == nextKey) {
                            previewLoadFailed = true
                            updateStatus()
                        }
                    },
                )
            }
        }
        updateStatus()
    }

    fun viewport(maxSize: Int): MediaPreviewViewport {
        val current = item ?: return MediaPreviewViewport(0, 0)
        return mediaPreviewViewport(maxSize, current.width, current.height)
    }

    fun dispose() {
        clearImage()
        item = null
        status.visibility = View.GONE
    }

    private fun updateStatus() {
        val message = if (previewLoadFailed) "Errore" else null
        status.text = message.orEmpty()
        status.isVisible = message != null
        status.setBackgroundColor(0x99000000.toInt())
    }

    private fun clearImage() {
        modelKey = null
        previewLoadFailed = false
        image.dispose()
        image.setImageDrawable(null)
    }
}

private fun MediaItem.previewModel(): String =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        fallbackUrl ?: url
    } else {
        url
    }

private const val MIN_PREVIEW_EDGE_RATIO = 0.24f
