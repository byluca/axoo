package com.axoo.features.posts.presentation.posteditor.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import coil.load
import com.axoo.features.posts.domain.RemoteMediaType
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp

@SuppressLint("ViewConstructor")
internal class NativePostEditorToolbarView(
    context: Context,
) : FrameLayout(context) {

    private var onGifTap: () -> Unit = {}
    private var onStickerTap: () -> Unit = {}
    private var onMemeTap: () -> Unit = {}
    private var onPhotoTap: () -> Unit = {}
    private var onPollTap: () -> Unit = {}
    private var hasPoll = false
    private val pollButton = ToolbarButtonView(context, "Sondaggio", "chart.svg")
    private var hasPhoto = false
    private val photoButton = ToolbarButtonView(context, "Foto", "photo.svg")

    private var navigationBottomInset = 0
    private var showNavBarPadding = true
    private var activePicker: RemoteMediaType? = null
    private var hasGif = false
    private var hasSticker = false
    private var hasMeme = false

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setPadding(dp(4), 0, dp(4), 0)
        background = GradientDrawable().apply {
            setColor(NativeColors.card)
            cornerRadius = dp(PostEditorStyle.toolbarRadius).toFloat()
            setStroke(dp(1).coerceAtLeast(1), NativeColors.cardBorder)
        }
        clipToOutline = true
    }

    private val gifButton = ToolbarButtonView(
        context = context,
        label = "GIF",
        iconAsset = "gif.svg",
        selectedIconAsset = "gif-fill.svg",
    )

    private val stickerButton = ToolbarButtonView(
        context = context,
        label = "Sticker",
        iconAsset = "smiley-sticker.svg",
        selectedIconAsset = "smiley-sticker-fill.svg",
    )
    private val memeButton = ToolbarButtonView(context, "Meme", "tears-of-joy.svg")

    init {
        setBackgroundColor(Color.TRANSPARENT)

        addView(row, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START,
        ))

        row.addView(photoButton, buttonLayoutParams(photoButton))
        row.addView(pollButton, buttonLayoutParams(pollButton))
        row.addView(gifButton, buttonLayoutParams(gifButton))
        row.addView(stickerButton, buttonLayoutParams(stickerButton))
        row.addView(memeButton, buttonLayoutParams(memeButton))
        pollButton.setOnClickListener { onPollTap() }
        photoButton.setOnClickListener { onPhotoTap() }

        gifButton.setOnClickListener {
            onGifTap()
        }
        stickerButton.setOnClickListener { onStickerTap() }
        memeButton.setOnClickListener {
            onMemeTap()
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            navigationBottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            applyInsets()
            insets
        }
        ViewCompat.requestApplyInsets(this)
    }

    fun setCallbacks(
        onGifTap: () -> Unit,
        onStickerTap: () -> Unit,
        onMemeTap: () -> Unit,
        onPhotoTap: () -> Unit,
        onPollTap: () -> Unit = {},
    ) {
        this.onGifTap = onGifTap
        this.onStickerTap = onStickerTap
        this.onMemeTap = onMemeTap
        this.onPhotoTap = onPhotoTap
        this.onPollTap = onPollTap
    }

    fun bind(
        hasGif: Boolean,
        hasSticker: Boolean,
        hasMeme: Boolean,
        hasPhoto: Boolean,
        hasPoll: Boolean = false,
        pollEnabled: Boolean = true,
    ) {
        this.hasGif = hasGif
        this.hasSticker = hasSticker
        this.hasMeme = hasMeme
        this.hasPhoto = hasPhoto
        this.hasPoll = hasPoll
        pollButton.isEnabled = pollEnabled
        pollButton.alpha = if (pollEnabled) 1f else 0.4f
        bindButtons()
        applyInsets()
    }

    fun setActivePicker(mode: RemoteMediaType?) {
        if (activePicker == mode) return
        activePicker = mode
        bindButtons()
    }

    private fun bindButtons() {
        pollButton.bind(tint = if (hasPoll) NativeColors.accent else NativeColors.textSecondary, selected = hasPoll)
        photoButton.bind(tint = if (hasPhoto) NativeColors.accent else NativeColors.textSecondary, selected = hasPhoto)
        gifButton.bind(
            tint = if (hasGif || activePicker == RemoteMediaType.Gif) {
                NativeColors.accent
            } else {
                NativeColors.textSecondary
            },
            selected = hasGif,
        )
        stickerButton.bind(
            tint = if (hasSticker || activePicker == RemoteMediaType.Sticker) NativeColors.accent else NativeColors.textSecondary,
            selected = hasSticker,
        )
        memeButton.bind(
            tint = if (hasMeme || activePicker == RemoteMediaType.Meme) {
                NativeColors.accent
            } else {
                NativeColors.textSecondary
            },
            selected = hasMeme,
        )
    }

    fun setNavigationBottomPaddingEnabled(enabled: Boolean) {
        if (showNavBarPadding == enabled) return
        showNavBarPadding = enabled
        applyInsets()
    }

    private fun applyInsets() {
        val bottom = dp(4) + if (showNavBarPadding) navigationBottomInset else 0
        setPadding(dp(PostEditorStyle.outerInset), dp(4), dp(PostEditorStyle.outerInset), bottom)
    }

    private fun buttonLayoutParams(button: ToolbarButtonView) =
        LinearLayout.LayoutParams(button.preferredWidth.toInt(), dp(PostEditorStyle.toolbarHeight), 1f)
}

@SuppressLint("ViewConstructor")
private class ToolbarButtonView(
    context: Context,
    label: String,
    private val iconAsset: String,
    private val selectedIconAsset: String? = null,
) : LinearLayout(context) {

    private var currentIconAsset: String? = null

    private val icon = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    private val text = TextView(context).apply {
        this.text = label
        textSize = PostEditorStyle.toolTextSize
        typeface = context.baloo2Typeface(500)
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 9, PostEditorStyle.toolTextSize.toInt(), 1, TypedValue.COMPLEX_UNIT_SP)
    }

    val preferredWidth: Float
        get() = text.paint.measureText(text.text.toString()) + context.dp(27)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        contentDescription = label
        setPadding(context.dp(3), 0, context.dp(3), 0)
        currentIconAsset = iconAsset
        icon.load(assetIcon(iconAsset))
        addView(icon, LayoutParams(context.dp(18), context.dp(18)))
        addView(text, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
            marginStart = context.dp(3)
        })
    }

    fun bind(
        tint: Int,
        selected: Boolean = false,
    ) {
        val nextIconAsset = if (selected) selectedIconAsset ?: iconAsset else iconAsset
        if (nextIconAsset != currentIconAsset) {
            currentIconAsset = nextIconAsset
            icon.load(assetIcon(nextIconAsset))
        }
        icon.imageTintList = ColorStateList.valueOf(tint)
        text.setTextColor(tint)
        background = RippleDrawable(
            ColorStateList.valueOf(NativeColors.pressedOverlay),
            ColorDrawable(Color.TRANSPARENT),
            ColorDrawable(Color.WHITE),
        )
    }

    private fun assetIcon(asset: String) = "file:///android_asset/icons/$asset"

}
