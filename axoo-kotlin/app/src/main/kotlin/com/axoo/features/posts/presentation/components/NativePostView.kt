/*
 * Disegna la scheda di un post.
 *
 * È un componente condiviso: viene usato nel feed, in Post Detail e nelle
 * attività del profilo. Non conosce la schermata che lo contiene.
 */
package com.axoo.features.posts.presentation.components

import com.axoo.ui.text.withWebLinks
import com.axoo.ui.text.WebLinkTouch
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.axoo.engine.emoji.catalog.EmojiData
import com.axoo.engine.emoji.image.CoilBitmapLoader
import com.axoo.engine.emoji.list.EmojiFrameCoordinator
import com.axoo.engine.emoji.list.EmojiInlineConsumer
import com.axoo.engine.emoji.text.AnimatedEmojiRenderer
import com.axoo.engine.emoji.text.AnimatedEmojiSpan
import com.axoo.engine.emoji.text.EmojiOnlyClassifier
import com.axoo.engine.emoji.text.EmojiTextMode
import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.engine.lottie.LottieHost
import com.axoo.features.posts.domain.ImageMeta
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.presentation.sharing.PostShare
import com.axoo.features.posts.domain.VoteState
import com.axoo.ui.avatar.isVerifiedAvatar
import com.axoo.ui.avatar.buildAvatarUrl
import com.axoo.ui.components.NativePillDefaults
import com.axoo.ui.components.cardPixelsPerPoint
import com.axoo.ui.formatting.formatTimeAgo
import com.axoo.ui.formatting.isContentEdited
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.interTypeface
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val EMOJI_RENDER_TAG = "EmojiTextRender"

internal enum class NativePostPresentation(
    val bodyTextPoints: Float,
    val maxMediaHeightPoints: Float,
    val maxPortraitMediaHeightPoints: Float = maxMediaHeightPoints,
) {
    Feed(bodyTextPoints = 17f, maxMediaHeightPoints = 240f, maxPortraitMediaHeightPoints = 480f),
    Detail(bodyTextPoints = 17f, maxMediaHeightPoints = 560f),
}

internal class NativePostView(
    context: Context,
    private val presentation: NativePostPresentation = NativePostPresentation.Feed,
) : android.widget.FrameLayout(context), EmojiInlineConsumer, LottieHost {
    // La geometria segue la larghezza di riferimento; i testi usano gli sp di Android.
    private var pixelsPerPoint = context.cardPixelsPerPoint()
    private fun points(value: Float): Float = value * pixelsPerPoint

    private var post: Post? = null
    private var onOpen: ((Post) -> Unit)? = null
    private var onUpvote: ((Post) -> Unit)? = null
    private var onDownvote: ((Post) -> Unit)? = null
    private var onComment: ((Post) -> Unit)? = null
    private var onMenu: ((Post, Boolean) -> Unit)? = null
    private var onAuthor: ((String) -> Unit)? = null
    private var onPollVote: ((Post, String) -> Unit)? = null
    private val pollRenderer = PostPollRenderer(this)
    private var pressedPollOption: String? = null
    private val pollAccessibility = object : ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val id = pollRenderer.optionAt(x, y) ?: return INVALID_ID
            return post?.poll?.options?.indexOfFirst { it.id == id }?.takeIf { it >= 0 } ?: INVALID_ID
        }

        override fun getVisibleVirtualViews(ids: MutableList<Int>) {
            post?.poll?.options?.indices?.forEach(ids::add)
        }

        override fun onPopulateNodeForVirtualView(id: Int, node: AccessibilityNodeInfoCompat) {
            val poll = post?.poll
            val option = poll?.options?.getOrNull(id)
            node.className = "android.widget.CheckBox"
            node.text = buildString {
                append(option?.text.orEmpty())
                if (poll?.hasVoted == true && !poll.isSending) append(", ${poll.percentages().getOrElse(id) { 0 }} per cento")
            }
            node.isCheckable = true
            node.isChecked = option != null && poll?.myOptionId == option.id
            node.isEnabled = poll != null && !poll.hasVoted && !poll.isSending
            pollRenderer.optionBounds(id)?.let { rect ->
                node.setBoundsInParent(Rect(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt()))
            } ?: node.setBoundsInParent(Rect(0, 0, 1, 1))
            if (node.isEnabled) node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        }

        override fun onPerformActionForVirtualView(id: Int, action: Int, arguments: Bundle?): Boolean {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
            val poll = post?.poll ?: return false
            if (poll.hasVoted || poll.isSending) return false
            val option = poll.options.getOrNull(id) ?: return false
            performPollVote(option.id)
            return true
        }
    }

    private val cardRect = RectF()
    private val avatarRect = RectF()
    private val authorRect = RectF()
    private val upvoteRect = RectF()
    private val downvoteRect = RectF()
    private val commentRect = RectF()
    private val shareRect = RectF()
    private val menuRect = RectF()
    private val flairRect = RectF()
    private val mediaRect = RectF()
    private val tmpRect = RectF()
    private val avatarClipPath = Path()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = points(NativePillDefaults.borderWidthDp)
        color = NativePillDefaults.borderColor
    }
    private val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val avatarBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val scorePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val actionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val flairPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private var bodyLayout: StaticLayout? = null
    private var emojiTextMode = EmojiTextMode.NormalText
    private var bodyText: CharSequence = ""
    private val webLinks = WebLinkTouch(this)
    private var authorText = ""
    private var metaText = ""
    private var timeAgo = ""
    private var measuredForWidth = 0
    private var measuredHeightPx = 0
    private var bodyLeft = 0f
    private var bodyTop = 0f
    private var bodyWidth = 0
    private var flairWidth = 0f
    private var commentsText = "0"
    private val scoreAnimation = VoteScoreAnimation { invalidate() }
    private var authorBaseline = 0f
    private var metaBaseline = 0f
    private var authorLeft = 0f
    private var metaLeft = 0f
    private var avatarUrl: String? = null
    private var avatarBitmap: Bitmap? = null
    private var avatarJob: Job? = null
    private var mediaMeta: ImageMeta? = null
    private val mediaView = MediaImageView(context).apply {
        onReady = { measuredForWidth = 0; requestLayout() }
    }
    internal val isShareMediaReady: Boolean get() = mediaMeta == null || mediaView.ready
    private val emojiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val emojiRenderer = AnimatedEmojiRenderer(
        context = context.applicationContext,
        hostView = this,
    )
    private var emojiFrameCoordinator: EmojiFrameCoordinator? = null
    // Fase 2.1: Le celle nascono congelate. Verranno riattivate dal RecyclerView per non sprecare risorse.
    private var emojiAnimating = false

    init {
        addView(mediaView)
        isClickable = true
        setWillNotDraw(false)
        configureCardShadow()
        authorPaint.typeface = context.baloo2Typeface(700)
        metaPaint.typeface = context.baloo2Typeface(400)
        bodyPaint.typeface = context.interTypeface(400)
        scorePaint.typeface = context.baloo2Typeface(700)
        actionPaint.typeface = context.baloo2Typeface(600)
        flairPaint.typeface = context.baloo2Typeface(700)
        configureTextSizes()
        bodyPaint.isSubpixelText = true
        bodyPaint.isLinearText = true
    }

    private fun configureCardShadow() {
        elevation = 0f
        bgPaint.clearShadowLayer()
    }

    private fun configureTextSizes() {
        authorPaint.textSize = context.sp(if (presentation == NativePostPresentation.Feed) 16f else 15f)
        metaPaint.textSize = context.sp(13f)
        bodyPaint.textSize = context.sp(presentation.bodyTextPoints)
        scorePaint.textSize = context.sp(13f)
        actionPaint.textSize = context.sp(13f)
        flairPaint.textSize = context.sp(11f)
    }

    private fun configureBody(post: Post) {
        emojiTextMode = if (presentation == NativePostPresentation.Feed) {
            EmojiTextMode.NormalText
        } else {
            EmojiOnlyClassifier.classify(post.body, post.entities)
        }
        val textSizeSp = bodySpForMode(emojiTextMode, presentation.bodyTextPoints)
        bodyPaint.textSize = context.sp(textSizeSp)
        bodyText = withWebLinks(buildNativeBodyText(
            post = post,
            emojiSizePx = (bodyPaint.textSize * emojiDpForMode(emojiTextMode, presentation.bodyTextPoints) / textSizeSp).roundToInt(),
        ))
    }

    /** Riempie la View con tutti i dati del post (testo, utente, media, ecc). */
    fun bind(
        post: Post,
        onOpen: (Post) -> Unit,
        onUpvote: (Post) -> Unit,
        onDownvote: (Post) -> Unit,
        onComment: (Post) -> Unit,
        onMenu: (Post, Boolean) -> Unit,
        onAuthor: (String) -> Unit = {},
        onPollVote: (Post, String) -> Unit = { _, _ -> },
    ) {
        val previousPost = this.post
        val sameItem = previousPost?.id == post.id
        this.post = post
        this.onOpen = onOpen
        this.onUpvote = onUpvote
        this.onDownvote = onDownvote
        this.onComment = onComment
        this.onMenu = onMenu
        this.onAuthor = onAuthor
        this.onPollVote = onPollVote
        ViewCompat.setAccessibilityDelegate(this, if (post.poll != null) pollAccessibility else null)
        contentDescription = "${post.nickname}. ${post.body}"
        pollAccessibility.invalidateRoot()
        pollRenderer.bind(post.poll, sameItem)
        configureBody(post)
        authorText = post.nickname.trim()
        timeAgo = buildString {
            append(formatTimeAgo(post.createdAt))
            if (isContentEdited(post.createdAt, post.updatedAt)) append(" · Modificato")
        }
        commentsText = post.commentsCount.toString()
        scoreAnimation.update(post.upvotes - post.downvotes, post.myVote, sameItem)
        bindAvatar(post)
        bindMedia(post.media)
        measuredForWidth = 0
        requestLayout()
    }

    /** Aggiorna solo la grafica dei voti (freccette) senza ricaricare tutto il post. */
    fun bindVote(post: Post) {
        if (this.post?.id != post.id) return
        if (this.post?.poll != post.poll) {
            pollRenderer.bind(post.poll, sameItem = true)
            ViewCompat.setAccessibilityDelegate(this, if (post.poll != null) pollAccessibility else null)
            pollAccessibility.invalidateRoot()
            measuredForWidth = 0
            requestLayout()
        }
        this.post = post
        val oldWidth = maxOf(points(44f), scorePaint.measureText(scoreAnimation.currentScore.toString()) + points(4f))
        scoreAnimation.update(post.upvotes - post.downvotes, post.myVote, sameItem = true)
        val nextWidth = maxOf(points(44f), scorePaint.measureText(scoreAnimation.currentScore.toString()) + points(4f))
        if (oldWidth != nextWidth) {
            measuredForWidth = 0
            requestLayout()
        }
        invalidate()
    }

    /** Spegne le animazioni ed eventi quando il post esce dallo schermo. */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        avatarJob?.cancel()
        avatarJob = null
        emojiRenderer.setAnimating(false)
        emojiRenderer.refreshHostAttachment()
        pollRenderer.detach()
    }

    /** Riaccende le animazioni quando il post torna visibile. */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        emojiRenderer.refreshHostAttachment()
        emojiRenderer.setAnimating(emojiAnimating)
        pollRenderer.refreshAttachment()
        pollRenderer.setAnimating(emojiAnimating)
        post?.let { bindAvatar(it) }

    }

    override val isAttachedForLottie: Boolean
        get() = isAttachedToWindow

    /** Comunica allo schermo di ridisegnare l'area dove c'è un'emoji animata. */
    override fun invalidateLottie(bounds: Rect?) {
        if (!isAttachedForLottie) return
        val coordinator = emojiFrameCoordinator
        if (coordinator != null) {
            coordinator.requestInvalidate(this)
        } else {
            invalidate()
        }
    }

    /** Decide se le emoji animate devono muoversi o stare ferme. */
    override fun setEmojiAnimating(value: Boolean) {
        emojiAnimating = value
        mediaView.animationsRunning = value
        pollRenderer.setAnimating(value)
        if (isAttachedToWindow) {
            emojiRenderer.setAnimating(value)
        }
    }

    /** Sincronizza il movimento di tutte le emoji presenti sullo schermo. */
    override fun setEmojiFrameCoordinator(coordinator: EmojiFrameCoordinator?) {
        if (emojiFrameCoordinator !== coordinator) {
            emojiFrameCoordinator?.cancel(this)
            emojiFrameCoordinator = coordinator
        }
        emojiRenderer.setFrameCoordinator(coordinator)
        pollRenderer.setCoordinator(coordinator)
    }

    /** Controlla se le emoji sono attualmente in riproduzione. */
    override fun isEmojiPlaying(): Boolean = emojiRenderer.isPlaying || pollRenderer.isPlaying()

    /** Calcola esattamente l'altezza e la larghezza di ogni componente del post. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        ensureLayout(width)
        mediaView.measure(MeasureSpec.makeMeasureSpec(mediaRect.width().toInt(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(mediaRect.height().toInt(), MeasureSpec.EXACTLY))
        setMeasuredDimension(width, measuredHeightPx)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        mediaView.layout(mediaRect.left.toInt(), mediaRect.top.toInt(), mediaRect.right.toInt(), mediaRect.bottom.toInt())
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        configureTextSizes()
        post?.let(::configureBody)
        measuredForWidth = 0
        requestLayout()
        invalidate()
    }

    /** Disegna fisicamente pixel per pixel l'intero post (testi, sfondo, icone, immagini). */
    override fun onDraw(canvas: Canvas) {
        val post = post ?: return
        applyColors()
        ensureLayout(width)

        bgPaint.color = NativePillDefaults.backgroundColor
        cardStrokePaint.color = NativePillDefaults.borderColor
        val cardRadius = points(14f)
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, bgPaint)
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardStrokePaint)

        avatarPaint.color = NativeColors.surface
        canvas.drawOval(avatarRect, avatarPaint)
        val bitmap = avatarBitmap
        if (bitmap != null && !bitmap.isRecycled) {
            canvas.save()
            canvas.clipPath(avatarClipPath)
            canvas.drawBitmap(bitmap, null, avatarRect, avatarBitmapPaint)
            canvas.restore()
        } else {
            avatarPaint.color = NativeColors.interactionInactive.withAlpha(0x33)
            canvas.drawCircle(avatarRect.centerX(), avatarRect.centerY(), points(7f), avatarPaint)
            val avatarIconSize = points(18f)
            tmpRect.set(
                avatarRect.centerX() - avatarIconSize / 2f,
                avatarRect.centerY() - avatarIconSize / 2f,
                avatarRect.centerX() + avatarIconSize / 2f,
                avatarRect.centerY() + avatarIconSize / 2f,
            )
            drawIcon(canvas, NativeIcons.AVATAR, tmpRect, NativeColors.interactionInactive.withAlpha(0x88))
        }

        canvas.drawText(authorText, authorLeft, authorBaseline, authorPaint)
        if (isVerifiedAvatar(post?.avatarConfig)) {
            val badgeLeft = authorLeft + authorPaint.measureText(authorText) + points(3f)
            val badgeTop = authorBaseline + (authorPaint.ascent() + authorPaint.descent()) / 2f - points(8f)
            tmpRect.set(badgeLeft, badgeTop, badgeLeft + points(16f), badgeTop + points(16f))
            drawIcon(canvas, NativeIcons.VERIFIED, tmpRect, Color.rgb(34, 139, 230))
        }
        canvas.drawText(metaText, metaLeft, metaBaseline, metaPaint)

        if (post.flair.isNotEmpty()) {
            val flairAppearance = flairAppearance(post.flair)
            val flairLabel = flairDisplayLabel(post.flair)
            pillPaint.color = flairAppearance.background
            flairPaint.color = flairAppearance.foreground
            val pillLeft = flairRect.left
            val pillTop = flairRect.top
            val pillBottom = flairRect.bottom
            val pillCenterY = (pillTop + pillBottom) / 2f
            tmpRect.set(pillLeft, pillTop, pillLeft + flairWidth, pillBottom)
            val flairRadius = minOf(points(14f), flairRect.height() / 2f)
            canvas.drawRoundRect(tmpRect, flairRadius, flairRadius, pillPaint)
            val iconSize = points(13f)
            flairAppearance.iconName?.let { iconName ->
                val iconLeft = pillLeft + points(8f)
                val iconTop = pillCenterY - iconSize / 2f
                tmpRect.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                drawIcon(canvas, iconName, tmpRect, flairAppearance.foreground)
            }
            val flairBaseline = pillCenterY - (flairPaint.ascent() + flairPaint.descent()) / 2f
            val textInset = if (flairAppearance.iconName == null) 9f else 27f
            val visibleLabel = TextUtils.ellipsize(flairLabel, flairPaint,
                (flairRect.width() - points(textInset + 9f)).coerceAtLeast(0f), TextUtils.TruncateAt.END)
            canvas.drawText(visibleLabel.toString(), pillLeft + points(textInset), flairBaseline, flairPaint)
        }

        canvas.save()
        canvas.translate(bodyLeft, bodyTop)
        // Tecnica Telegram: allinea le emoji animate al testo conservando la loro ultima posizione valida finché il Layout non le ridisegna sullo schermo.
        emojiRenderer.beforeTextDraw(bodyLayout, bodyText)
        bodyLayout?.draw(canvas)
        emojiRenderer.draw(canvas)
        canvas.restore()

        pollRenderer.draw(canvas)

        val upColor = blendColor(
            voteUpColor(scoreAnimation.oldVoteState),
            voteUpColor(scoreAnimation.currentVoteState),
            scoreAnimation.progress,
        )
        val downColor = blendColor(
            voteDownColor(scoreAnimation.oldVoteState),
            voteDownColor(scoreAnimation.currentVoteState),
            scoreAnimation.progress,
        )
        iconPaint.style = Paint.Style.STROKE
        val voteIconSize = points(32f)
        tmpRect.set(
            upvoteRect.centerX() - voteIconSize / 2f,
            upvoteRect.centerY() - voteIconSize / 2f,
            upvoteRect.centerX() + voteIconSize / 2f,
            upvoteRect.centerY() + voteIconSize / 2f,
        )
        drawIcon(canvas, NativeIcons.POST_UP, tmpRect, upColor)
        tmpRect.set(
            downvoteRect.centerX() - voteIconSize / 2f,
            downvoteRect.centerY() - voteIconSize / 2f,
            downvoteRect.centerX() + voteIconSize / 2f,
            downvoteRect.centerY() + voteIconSize / 2f,
        )
        drawIcon(canvas, NativeIcons.POST_DOWN, tmpRect, downColor)
        scorePaint.color = blendColor(
            voteScoreColor(scoreAnimation.oldVoteState),
            voteScoreColor(scoreAnimation.currentVoteState),
            scoreAnimation.progress,
        )
        drawRollingNumber(
            canvas = canvas,
            paint = scorePaint,
            oldValue = scoreAnimation.oldScore,
            currentValue = scoreAnimation.currentScore,
            progress = scoreAnimation.progress,
            left = upvoteRect.left,
            right = upvoteRect.right,
            centerY = (upvoteRect.centerY() + downvoteRect.centerY()) * 0.5f,
            halfHeightPx = points(12f),
            gapPx = points(2f),
        )

        iconPaint.style = Paint.Style.FILL
        iconPaint.color = NativeColors.interactionInactive
        val actionIconSize = points(22f)
        val actionIconY = commentRect.centerY() - actionIconSize / 2f
        val actionTextY = commentRect.centerY() - (actionPaint.ascent() + actionPaint.descent()) / 2f
        tmpRect.set(
            cardRect.left + points(16f),
            actionIconY,
            cardRect.left + points(16f) + actionIconSize,
            actionIconY + actionIconSize,
        )
        drawIcon(canvas, NativeIcons.COMMENT, tmpRect, NativeColors.interactionInactive)
        actionPaint.color = NativeColors.interactionInactive
        canvas.drawText(commentsText, cardRect.left + points(44f), actionTextY, actionPaint)
        tmpRect.set(
            shareRect.centerX() - actionIconSize / 2f,
            actionIconY,
            shareRect.centerX() + actionIconSize / 2f,
            actionIconY + actionIconSize,
        )
        drawIcon(canvas, NativeIcons.SHARE, tmpRect, NativeColors.interactionInactive)
        tmpRect.set(
            menuRect.centerX() - actionIconSize / 2f,
            actionIconY,
            menuRect.centerX() + actionIconSize / 2f,
            actionIconY + actionIconSize,
        )
        drawIcon(canvas, NativeIcons.MENU, tmpRect, NativeColors.interactionInactive)
    }

    /** Capisce cosa l'utente ha toccato (Like, Profilo, Media). */
    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        if (post?.poll != null) pollAccessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
        else super.dispatchHoverEvent(event)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val post = post ?: return false
        val option = pollRenderer.optionAt(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressedPollOption = option
            MotionEvent.ACTION_CANCEL -> { pressedPollOption = null; return true }
            MotionEvent.ACTION_MOVE -> if (option != pressedPollOption) pressedPollOption = null
            MotionEvent.ACTION_UP -> if (option != null) {
                if (pressedPollOption == option) performPollVote(option)
                pressedPollOption = null
                return true
            }
        }
        if (webLinks.onTouch(event, bodyLayout, bodyLeft, bodyTop)) return true
        if (event.action != MotionEvent.ACTION_UP) return true
        val x = event.x
        val y = event.y
        when {
            avatarRect.contains(x, y) || authorRect.contains(x, y) -> onAuthor?.invoke(post.userId)
            upvoteRect.contains(x, y) -> {
                com.axoo.core.haptics.Haptics.click(this)
                onUpvote?.invoke(post)
            }
            downvoteRect.contains(x, y) -> {
                com.axoo.core.haptics.Haptics.click(this)
                onDownvote?.invoke(post)
            }
            commentRect.contains(x, y) -> onComment?.invoke(post)
            shareRect.contains(x, y) -> PostShare.share(this)
            menuRect.contains(x, y) -> onMenu?.invoke(post, false)
            mediaRect.contains(x, y) && post.media?.provider == "axoo" && post.media.type == "sticker" ->
                com.axoo.features.posts.presentation.stickers.StickerActions.show(this, post.media)
            else -> onOpen?.invoke(post)
        }
        return true
    }

    private fun performPollVote(optionId: String) {
        val target = post ?: return
        val poll = target.poll ?: return
        if (poll.hasVoted || poll.isSending) return
        com.axoo.core.haptics.Haptics.click(this)
        onPollVote?.invoke(target, optionId)
    }

    /** Pre-calcola le posizioni esatte di tutti i testi e gli elementi prima di disegnarli. */
    private fun ensureLayout(width: Int) {
        if (width == measuredForWidth && bodyLayout != null) return
        measuredForWidth = width
        val nextScale = context.cardPixelsPerPoint(width)
        if (pixelsPerPoint != nextScale) {
            pixelsPerPoint = nextScale
            configureTextSizes()
            post?.let(::configureBody)
            cardStrokePaint.strokeWidth = points(NativePillDefaults.borderWidthDp)
            configureCardShadow()
        }
        ensureCardLayout(width)
    }

    /** Shared iOS geometry in points: 16-point card insets and media below votes. */
    private fun ensureCardLayout(width: Int) {
        val cardLeft = points(16f)
        val cardRight = width - cardLeft
        val top = points(4f)
        val contentLeft = cardLeft + points(16f)
        val contentTop = top + points(14f)
        val scoreHeight = scorePaint.fontMetrics.descent - scorePaint.fontMetrics.ascent
        val voteWidth = maxOf(points(44f), scorePaint.measureText(scoreAnimation.currentScore.toString()) + points(4f))
        bodyLeft = contentLeft
        bodyWidth = (cardRight - contentLeft - points(16f) - voteWidth - points(8f))
            .toInt().coerceAtLeast(1)

        val avatarSize = points(if (presentation == NativePostPresentation.Feed) 36f else 34f)
        val headerHeight = maxOf(avatarSize, authorPaint.fontMetrics.descent - authorPaint.fontMetrics.ascent,
            metaPaint.fontMetrics.descent - metaPaint.fontMetrics.ascent)
        avatarRect.set(contentLeft, contentTop + (headerHeight - avatarSize) / 2f,
            contentLeft + avatarSize, contentTop + (headerHeight + avatarSize) / 2f)
        avatarClipPath.reset()
        avatarClipPath.addOval(avatarRect, Path.Direction.CW)
        authorLeft = contentLeft + avatarSize + points(10f)
        authorBaseline = contentTop + headerHeight / 2f - (authorPaint.ascent() + authorPaint.descent()) / 2f
        metaBaseline = contentTop + headerHeight / 2f - (metaPaint.ascent() + metaPaint.descent()) / 2f
        layoutHeader(contentLeft + bodyWidth)
        authorRect.set(authorLeft, contentTop, metaLeft - points(6f), contentTop + headerHeight)

        var y = contentTop + headerHeight + points(12f)
        val flair = post?.flair.orEmpty()
        if (flair.isNotBlank()) {
            val appearance = flairAppearance(flair)
            val inset = if (appearance.iconName == null) 18f else 36f
            flairWidth = minOf(bodyWidth.toFloat(), flairPaint.measureText(flairDisplayLabel(flair)) + points(inset))
            val flairHeight = flairPaint.fontMetrics.descent - flairPaint.fontMetrics.ascent + points(8f)
            flairRect.set(contentLeft, y, contentLeft + flairWidth, y + flairHeight)
            y += flairHeight + points(12f)
        } else {
            flairRect.setEmpty()
        }
        bodyTop = y
        bodyLayout = bodyPaint.staticLayout(bodyText, bodyWidth, lineSpacingMultiplier = 1f)
        emojiRenderer.updateFromText(bodyText)
        emojiRenderer.setAnimating(emojiAnimating && isAttachedToWindow)
        y += bodyLayout?.height ?: 0

        val voteLeft = cardRight - points(16f) - voteWidth
        val touchSize = points(44f)
        upvoteRect.set(voteLeft, contentTop, voteLeft + voteWidth, contentTop + touchSize)
        val downTop = contentTop + points(48f) + scoreHeight
        downvoteRect.set(voteLeft, downTop, voteLeft + voteWidth, downTop + touchSize)
        val mediaWidth = (cardRight - points(16f) - contentLeft).toInt()
        val media = mediaMeta
        val mediaViewport = media?.let {
            val maxHeight = if (it.height > it.width) presentation.maxPortraitMediaHeightPoints
                else presentation.maxMediaHeightPoints
            postMediaViewportSize(mediaWidth, points(maxHeight).toInt(), it.width, it.height)
        }
        if (mediaViewport != null) {
            y = maxOf(y, downvoteRect.bottom) + points(12f)
            val mediaLeft = contentLeft + (mediaWidth - mediaViewport.width) / 2f
            mediaRect.set(mediaLeft, y, mediaLeft + mediaViewport.width, y + mediaViewport.height)
            y = mediaRect.bottom
        } else {
            mediaRect.setEmpty()
        }

        if (post?.poll != null) {
            y = pollRenderer.layout(contentLeft, maxOf(y, downvoteRect.bottom) + points(12f),
                cardRight - points(16f), pixelsPerPoint)
        }

        val contentHeight = maxOf(points(106f), y - contentTop)
        val footerTop = maxOf(contentTop + contentHeight, contentTop + points(92f) + scoreHeight) + points(8f)
        val footerHeight = maxOf(touchSize, actionPaint.descent() - actionPaint.ascent() + points(12f))
        measuredHeightPx = kotlin.math.ceil((footerTop + footerHeight + points(8f)).toDouble()).toInt()
        cardRect.set(cardLeft, top, cardRight, measuredHeightPx - points(4f))
        menuRect.set(cardRight - points(60f), footerTop, cardRight - points(16f), footerTop + footerHeight)
        val commentWidth = maxOf(points(64f), points(44f) + actionPaint.measureText(commentsText))
            .coerceAtMost(menuRect.left - contentLeft - points(52f))
        commentRect.set(contentLeft, footerTop, contentLeft + commentWidth, footerTop + footerHeight)
        shareRect.set(commentRect.right + points(4f), footerTop, commentRect.right + points(48f), footerTop + footerHeight)
        invalidateOutline()
    }

    private fun layoutHeader(headerRight: Float) {
        val rawAuthor = post?.nickname.orEmpty().trim().ifEmpty {
            "Anonimo"
        }
        val badgeWidth = if (isVerifiedAvatar(post?.avatarConfig)) points(20f) else 0f
        val authorGap = if (rawAuthor.isEmpty()) 0f else points(6f)
        val headerWidth = (headerRight - authorLeft).coerceAtLeast(0f)
        val campusName = post?.universityName.orEmpty().trim()
        val metadata = buildString {
            if (rawAuthor.isNotEmpty()) append("· ")
            if (campusName.isNotEmpty()) append(campusName).append(" · ")
            append(timeAgo)
        }
        metaText = TextUtils.ellipsize(
            metadata,
            metaPaint,
            (headerWidth - points(48f) - authorGap - badgeWidth).coerceAtLeast(0f),
            TextUtils.TruncateAt.END,
        ).toString()
        val maxAuthorWidth = (
            headerRight - authorLeft - metaPaint.measureText(metaText) - authorGap - badgeWidth
            ).coerceAtLeast(points(48f))
        authorText = TextUtils.ellipsize(
            rawAuthor,
            authorPaint,
            maxAuthorWidth,
            TextUtils.TruncateAt.END,
        ).toString()
        metaLeft = authorLeft + authorPaint.measureText(authorText) + badgeWidth + authorGap
    }

    /** Unisce matematicamente due colori per creare una sfumatura fluida (Lerp). */
    private fun blendColor(from: Int, to: Int, progress: Float): Int {
        val t = progress.coerceIn(0f, 1f)
        return Color.argb(
            lerp(Color.alpha(from).toFloat(), Color.alpha(to).toFloat(), t).roundToInt(),
            lerp(Color.red(from).toFloat(), Color.red(to).toFloat(), t).roundToInt(),
            lerp(Color.green(from).toFloat(), Color.green(to).toFloat(), t).roundToInt(),
            lerp(Color.blue(from).toFloat(), Color.blue(to).toFloat(), t).roundToInt(),
        )
    }

    private fun applyColors() {
        authorPaint.color = NativeColors.textPrimary
        metaPaint.color = NativeColors.textSecondary
        bodyPaint.color = NativeColors.textPrimary
        actionPaint.color = NativeColors.interactionInactive
    }

    /** Scarica e imposta l'immagine del profilo di chi ha scritto il post. */
    private fun bindAvatar(post: Post) {
        val nextUrl = buildAvatarUrl(post.userId, post.avatarConfig)
        val sizePx = points(96f).toInt()
        if (avatarUrl == nextUrl && avatarBitmap != null) return
        avatarUrl = nextUrl
        avatarBitmap = CoilBitmapLoader.peek(context.applicationContext, nextUrl, sizePx)
        avatarJob?.cancel()
        if (avatarBitmap != null) {
            invalidate()
            return
        }
        avatarJob = emojiScope.launch {
            val loaded = CoilBitmapLoader.load(context.applicationContext, nextUrl, sizePx)
            if (avatarUrl == nextUrl) {
                avatarBitmap = loaded
                invalidate()
            }
        }
    }

    private fun bindMedia(media: ImageMeta?) {
        mediaMeta = media
        mediaView.cornerRadius = points(12f)
        mediaView.scaleType = if (presentation == NativePostPresentation.Feed) android.widget.ImageView.ScaleType.FIT_CENTER
            else android.widget.ImageView.ScaleType.CENTER_CROP
        val url = if (media?.provider == "aup" && Build.VERSION.SDK_INT < 28) media.previewFallbackUrl ?: media.url else media?.url
        mediaView.bind(url, media?.thumbnailUrl.takeIf { media?.type == "gif" })
    }

    /** Anima in modo morbido il cambio di numero quando qualcuno mette un voto. */

    /** Restituisce il colore per la freccia in sù. */
    private fun voteUpColor(vote: VoteState): Int =
        if (vote == VoteState.Upvote) NativeColors.accent else NativeColors.interactionInactive

    /** Restituisce il colore per la freccia in giù. */
    private fun voteDownColor(vote: VoteState): Int =
        if (vote == VoteState.Downvote) NativeColors.downvoteActive else NativeColors.interactionInactive

    /** Restituisce il colore del punteggio. */
    private fun voteScoreColor(vote: VoteState): Int = when (vote) {
        VoteState.Upvote -> NativeColors.accent
        VoteState.Downvote -> NativeColors.downvoteActive
        VoteState.None -> NativeColors.textPrimary
    }

    /** Svuota completamente il post per riutilizzarlo e risparmiare memoria (RecyclerView). */
    fun recycle() {
        scoreAnimation.cancel()
        avatarJob?.cancel()
        avatarJob = null
        avatarUrl = null
        avatarBitmap = null
        mediaMeta = null
        mediaView.clear()
        post = null
        onPollVote = null
        pressedPollOption = null
        pollRenderer.clear()
        onAuthor = null
        bodyLayout = null
        bodyText = ""
        authorText = ""
        metaText = ""
        commentsText = "0"
        measuredForWidth = 0
        emojiRenderer.clear()
        invalidate()
    }

    private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

    /** Usa Coil per disegnare un'icona vettoriale e colorarla. */
    private fun drawIcon(canvas: Canvas, name: String, rect: RectF, color: Int) {
        val sizePx = points(48f).toInt()
        val data = nativeIconData(name)
        val bitmap = CoilBitmapLoader.peek(context.applicationContext, data, sizePx)
        if (bitmap == null) {
            CoilBitmapLoader.enqueue(context.applicationContext, data, sizePx) {
                invalidate()
            }
            return
        }
        val oldFilter: ColorFilter? = iconPaint.colorFilter
        iconPaint.style = Paint.Style.FILL
        iconPaint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, null, rect, iconPaint)
        iconPaint.colorFilter = oldFilter
    }
}

internal data class PostMediaViewport(
    val width: Int,
    val height: Int,
)

internal fun postMediaViewportSize(
    maxWidth: Int,
    maxHeight: Int,
    mediaWidth: Int,
    mediaHeight: Int,
): PostMediaViewport? {
    if (maxWidth <= 0 || maxHeight <= 0) return null

    val sourceWidth = mediaWidth.takeIf { it > 0 } ?: 4
    val sourceHeight = mediaHeight.takeIf { it > 0 } ?: 3
    val scale = minOf(
        maxWidth.toFloat() / sourceWidth,
        maxHeight.toFloat() / sourceHeight,
    )
    return PostMediaViewport(
        width = (sourceWidth * scale).toInt().coerceAtLeast(1),
        height = (sourceHeight * scale).toInt().coerceAtLeast(1),
    )
}

object NativeIcons {
    const val VERIFIED = "verification"
    const val POST_UP = "arrow-up-v2"
    const val POST_DOWN = "arrow-down-v2"
    const val COMMENT = "comment"
    const val SHARE = "share"
    const val MENU = "dots-three"
    const val AVATAR = "user"
    const val COMMENT_UP = "up-square-comment"
    const val COMMENT_DOWN = "down-square-comment"
    const val REPLY = "reply"
    const val FLAIR_MEME = "smiling-filled"
    const val FLAIR_PARTY = "party-filled"
    const val FLAIR_MESSAGES = "messages"
    const val FLAIR_HOME = "home2-filled"
    const val FLAIR_BOOK = "book-filled"
    const val FLAIR_SPOTTED = "heart-filled"
    val ALL = listOf(
        VERIFIED, POST_UP, POST_DOWN, COMMENT, SHARE, MENU, AVATAR, COMMENT_UP, COMMENT_DOWN, REPLY,
        FLAIR_MEME, FLAIR_PARTY, FLAIR_MESSAGES, FLAIR_HOME, FLAIR_BOOK, FLAIR_SPOTTED,
    )
}

internal fun prefetchNativeIcons(context: Context, sizePx: Int) {
    NativeIcons.ALL.forEach { name ->
        CoilBitmapLoader.prefetch(context, nativeIconData(name), sizePx)
    }
}

internal fun nativeIconData(name: String): String =
    CoilBitmapLoader.assetData("icons/$name.svg")

internal fun buildNativeBodyText(
    post: Post,
    emojiSizePx: Int,
): CharSequence {
    return buildNativeEmojiText(post.body, post.entities, emojiSizePx, post.id)
}

internal fun buildNativeEmojiText(
    text: String,
    entities: List<MessageEntity>,
    emojiSizePx: Int,
    debugId: String,
): CharSequence {
    if (entities.isEmpty()) {
        return text
    }

    val builder = SpannableStringBuilder()
    var cursor = 0
    entities
        .filter { it.type == MessageEntity.TYPE_CUSTOM_EMOJI || it.type == MessageEntity.TYPE_STATIC_EMOJI }
        .sortedBy { it.offset }
        .forEach { entity ->
            val start = entity.offset
            val end = start + entity.length
            if (start < cursor || start < 0 || end > text.length) return@forEach
            if (start > cursor) builder.append(text.substring(cursor, start))

            if (entity.type == MessageEntity.TYPE_STATIC_EMOJI) {
                builder.append(text.substring(start, end))
                cursor = end
                return@forEach
            }

            val emoji = EmojiData.byId[entity.customEmojiId]
            if (emoji == null) {
                if (com.axoo.BuildConfig.DEBUG) {
                    Log.d(
                        EMOJI_RENDER_TAG,
                        "missing_custom_emoji_id id=${entity.customEmojiId} postId=${debugId}"
                    )
                }
                builder.append(text.substring(start, end))
                cursor = end
                return@forEach
            }
            val spanStart = builder.length
            builder.append('\uFFFC')
            builder.setSpan(
                AnimatedEmojiSpan(
                    renderSizePx = emojiSizePx,
                    emojiIndex = entity.customEmojiId,
                    emojiName = emoji.name,
                    fallbackChar = emoji.fallback.ifBlank { text.substring(start, end) },
                ),
                spanStart,
                spanStart + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            cursor = end
    }
    if (cursor < text.length) builder.append(text.substring(cursor))
    return builder
}

internal fun TextPaint.staticLayout(
    text: CharSequence,
    width: Int,
    lineSpacingMultiplier: Float,
    alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
): StaticLayout {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        StaticLayout.Builder.obtain(text, 0, text.length, this, width)
            .setAlignment(alignment)
            .setIncludePad(false)
            .setLineSpacing(0f, lineSpacingMultiplier)
            .build()
    } else {
        @Suppress("DEPRECATION")
        StaticLayout(text, this, width, alignment, lineSpacingMultiplier, 0f, false)
    }
}

internal fun emojiDpForMode(mode: EmojiTextMode, normalTextSp: Float): Float = when (mode) {
    EmojiTextMode.OneEmoji -> 64f
    EmojiTextMode.TwoEmoji -> 56f
    EmojiTextMode.ThreeEmoji -> 48f
    EmojiTextMode.FourEmoji -> 40f
    EmojiTextMode.FiveEmoji -> 32f
    EmojiTextMode.NormalText -> normalTextSp * MIXED_TEXT_EMOJI_SCALE
}

internal fun bodySpForMode(mode: EmojiTextMode, normalTextSp: Float): Float = when (mode) {
    EmojiTextMode.OneEmoji -> 56f
    EmojiTextMode.TwoEmoji -> 50f
    EmojiTextMode.ThreeEmoji -> 42f
    EmojiTextMode.FourEmoji -> 34f
    EmojiTextMode.FiveEmoji -> 28f
    EmojiTextMode.NormalText -> normalTextSp
}

internal fun Context.dp(value: Float): Float = value * resources.displayMetrics.density
internal fun Context.sp(value: Float): Float = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_SP,
    value,
    resources.displayMetrics,
)
internal fun Int.withAlpha(alpha: Int): Int = (this and 0x00ffffff) or (alpha shl 24)

internal const val MIXED_TEXT_EMOJI_SCALE = 1.8f
internal const val MIXED_TEXT_LINE_SPACING = 1.12f
internal val whitespaceRegex = Regex("\\s+")

internal fun Context.typeface(weight: Int): Typeface {
    return baloo2Typeface(weight)
}
