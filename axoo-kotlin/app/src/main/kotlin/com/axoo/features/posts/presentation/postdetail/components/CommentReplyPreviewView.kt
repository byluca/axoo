package com.axoo.features.posts.presentation.postdetail.components

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import com.axoo.core.anim.AxooInterpolators
import com.axoo.engine.emoji.list.EmojiPlaybackGate
import com.axoo.engine.emoji.text.AnimatedEmojiTextView
import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.features.posts.domain.Comment
import com.axoo.features.posts.presentation.components.MIXED_TEXT_EMOJI_SCALE
import com.axoo.features.posts.presentation.components.buildCommentBodyText
import com.axoo.ui.components.NativePillDefaults
import com.axoo.ui.components.setControlShadow
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp
import com.axoo.ui.theme.interTypeface
import kotlin.math.roundToInt

/** Rounded reply pill above the composer. Only the close button acts on the conversation. */
internal class CommentReplyPreviewView(context: Context) : FrameLayout(context) {
    private var shown = false
    private var active = false
    private var playbackGate = EmojiPlaybackGate.Inactive
    private var boundBody: String? = null
    private var boundEntities = emptyList<MessageEntity>()
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private val fullHeight: Int
        get() = maxOf(context.dp(52), title.lineHeight + preview.lineHeight + context.dp(12))
    private val title = TextView(context).apply {
        typeface = context.baloo2Typeface(600)
        textSize = 14f
        setTextColor(NativeColors.accent)
        includeFontPadding = false
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.END
    }
    private val preview = AnimatedEmojiTextView(context).apply {
        setPlaybackAllowed(false)
        typeface = context.interTypeface(400)
        textSize = 12f
        setTextColor(NativeColors.textSecondary)
        includeFontPadding = false
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.END
    }
    private val replyIcon = ImageView(context).apply {
        load("file:///android_asset/icons/reply-bold.svg")
        setColorFilter(NativeColors.accent, PorterDuff.Mode.SRC_IN)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    private val close = ImageView(context).apply {
        load("file:///android_asset/icons/close.svg")
        setColorFilter(NativeColors.textSecondary, PorterDuff.Mode.SRC_IN)
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(context.dp(15), context.dp(15), context.dp(15), context.dp(15))
        isFocusable = true
        background = RippleDrawable(ColorStateList.valueOf(NativeColors.pressedOverlay), null,
            GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) })
    }
    private val surface = FrameLayout(context).apply {
        background = GradientDrawable().apply {
            setColor(NativePillDefaults.backgroundColor)
            setStroke(context.dp(NativePillDefaults.borderWidthDp).toInt().coerceAtLeast(1),
                NativePillDefaults.borderColor)
        }
        clipToOutline = true
        elevation = context.dp(NativePillDefaults.controlElevationDp)
        // Consume touches on the header; never highlight or scroll to the parent.
        isClickable = true
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(preview, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            marginStart = context.dp(40)
            marginEnd = context.dp(48)
        })
        addView(replyIcon, LayoutParams(context.dp(20), context.dp(20), Gravity.START or Gravity.CENTER_VERTICAL).apply {
            marginStart = context.dp(12)
        })
        addView(close, LayoutParams(context.dp(48), context.dp(48), Gravity.END or Gravity.CENTER_VERTICAL))
    }

    init {
        clipChildren = true
        clipToPadding = true
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(48), Gravity.BOTTOM).apply {
            bottomMargin = context.dp(4)
        })
        applyProgress(0f)
    }

    fun setOnCancel(callback: () -> Unit) = close.setOnClickListener { callback() }

    fun setActive(value: Boolean) {
        active = value
        updatePlayback()
    }

    fun setEmojiPlaybackGate(gate: EmojiPlaybackGate) {
        playbackGate = gate
        updatePlayback()
    }

    private fun updatePlayback() = preview.setPlaybackAllowed(shown && active && playbackGate.playbackAllowed)

    fun bind(nickname: String?, comment: Comment?, enabled: Boolean) {
        val visible = nickname != null
        if (visible) {
            title.text = "Rispondi a $nickname"
        }
        val body = if (!visible) "" else if (comment?.isDeleted == true) "Commento non disponibile" else comment?.body.orEmpty()
        val entities = if (visible && comment?.isDeleted == false) comment.entities else emptyList()
        if (boundBody != body || boundEntities != entities) {
            boundBody = body
            boundEntities = entities
            preview.setEmojiText(if (visible && comment != null) buildCommentReplyText(comment, (preview.textSize * MIXED_TEXT_EMOJI_SCALE).roundToInt()) else "")
        }
        close.isEnabled = visible && enabled
        preview.setPlaybackAllowed(visible && active && playbackGate.playbackAllowed)
        if (shown == visible) return
        shown = visible
        animator?.cancel()
        val target = if (visible) 1f else 0f
        if (!isAttachedToWindow) {
            applyProgress(target)
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = AxooInterpolators.NoBounceSpringDurationMs
            interpolator = AxooInterpolators.NoBounceSpring
            addUpdateListener { applyProgress(it.animatedValue as Float) }
            start()
        }
    }

    private fun applyProgress(value: Float) {
        progress = value
        // Mantiene il ritaglio durante la comparsa, poi lascia spazio all'ombra.
        clipChildren = value < 1f
        clipToPadding = value < 1f
        surface.alpha = value
        surface.visibility = if (value == 0f) INVISIBLE else VISIBLE
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Measure content at full size; reveal it upwards without squeezing text.
        surface.layoutParams.height = fullHeight - context.dp(4)
        (surface.background as GradientDrawable).cornerRadius = surface.layoutParams.height / 2f
        surface.setControlShadow(surface.layoutParams.height / 2f)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(fullHeight, MeasureSpec.EXACTLY))
        setMeasuredDimension(measuredWidth, (fullHeight * progress).roundToInt())
    }

    fun dispose() {
        animator?.cancel()
        animator = null
        preview.dispose()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        applyProgress(if (shown) 1f else 0f)
        super.onDetachedFromWindow()
    }

}

/** Collapse whitespace after creating spans, so source UTF-16 offsets stay valid. */
internal fun buildCommentReplyText(comment: Comment, emojiSizePx: Int): CharSequence {
    if (comment.isDeleted) return "Commento non disponibile"
    if (comment.body.isBlank() && comment.media != null) return if (comment.media.type == "sticker") "Sticker" else "GIF"
    val result = SpannableStringBuilder(buildCommentBodyText(comment, emojiSizePx))
    Regex("\\s+").findAll(result).toList().asReversed().forEach { match ->
        result.replace(match.range.first, match.range.last + 1, " ")
    }
    if (result.endsWith(" ")) result.delete(result.length - 1, result.length)
    if (result.startsWith(" ")) result.delete(0, 1)
    return result
}
