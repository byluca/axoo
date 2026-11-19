/*
 * Disegna la scheda di un commento.
 *
 * È un componente condiviso da Post Detail e dalle attività del profilo.
 * Riceve dati e callback senza conoscere la schermata che lo contiene.
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
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.axoo.core.haptics.Haptics
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
import com.axoo.features.posts.domain.Comment
import com.axoo.features.posts.domain.VoteState
import com.axoo.features.posts.presentation.postdetail.COMMENT_INDENT_DP
import com.axoo.features.posts.presentation.postdetail.CommentDeliveryState
import com.axoo.features.posts.presentation.postdetail.CommentUiModel
import com.axoo.features.posts.presentation.postdetail.FlattenedComment
import com.axoo.features.posts.presentation.postdetail.MAX_COMMENT_INDENT
import com.axoo.features.posts.presentation.postdetail.hasSameRenderedBody
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

internal class NativeCommentView(
    context: Context,
) : FrameLayout(context), EmojiInlineConsumer, LottieHost {

    private var pixelsPerPoint = context.cardPixelsPerPoint()
    private fun points(value: Float): Float = value * pixelsPerPoint

    private var item: CommentUiModel? = null
    private val comment: Comment? get() = item?.comment
    private var onUpvote: ((Comment) -> Unit)? = null
    private var onDownvote: ((Comment) -> Unit)? = null
    private var onDelete: ((Comment) -> Unit)? = null
    private var onMenu: ((Comment) -> Unit)? = null
    private var onOpen: ((Comment) -> Unit)? = null
    private var onReply: ((Comment) -> Unit)? = null
    private var onAuthor: ((String) -> Unit)? = null
    internal var thread: FlattenedComment? = null
        private set
    private var emojiTextMode: EmojiTextMode = EmojiTextMode.NormalText

    private val mediaRect = RectF()
    private val mediaView = MediaImageView(context)
    private val cardRect = RectF()
    private val avatarRect = RectF()
    private val authorRect = RectF()
    private val menuRect = RectF()
    private val replyRect = RectF()
    private val upvoteRect = RectF()
    private val downvoteRect = RectF()
    private val tmpRect = RectF()
    private val avatarClipPath = Path()
    private val threadPath = Path()
    private val threadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = points(1f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = points(NativePillDefaults.borderWidthDp)
        color = NativePillDefaults.borderColor
    }
    private val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val avatarBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val actionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val scorePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val deletedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private var bodyLayout: StaticLayout? = null
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
    private val scoreAnimation = VoteScoreAnimation { invalidate() }
    private var avatarUrl: String? = null
    private var avatarBitmap: Bitmap? = null
    private var avatarJob: Job? = null
    private var authorBaseline = 0f
    private var metaBaseline = 0f
    private var authorLeft = 0f
    private var metaLeft = 0f

    private val emojiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val emojiRenderer = AnimatedEmojiRenderer(
        context = context.applicationContext,
        hostView = this,
    )
    private var emojiFrameCoordinator: EmojiFrameCoordinator? = null

    // Le celle nascono congelate. Il RecyclerView invia il valore reale
    // tramite setEmojiAnimating(...) subito dopo l'aggancio (attach).
    private var emojiAnimating = false

    init {
        addView(mediaView)
        isClickable = true
        setWillNotDraw(false)
        elevation = 0f

        val regular = context.typeface(400)

        authorPaint.typeface = context.baloo2Typeface(700)
        metaPaint.typeface = regular
        bodyPaint.typeface = context.interTypeface(400)
        actionPaint.typeface = context.baloo2Typeface(600)
        scorePaint.typeface = context.baloo2Typeface(700)
        deletedPaint.typeface = context.interTypeface(400)

        configureTextSizes()

        bodyPaint.isSubpixelText = true
        bodyPaint.isLinearText = true

    }

    private fun configureTextSizes() {
        authorPaint.textSize = context.sp(15f)
        metaPaint.textSize = context.sp(13f)
        bodyPaint.textSize = context.sp(COMMENT_BODY_TEXT_POINTS)
        actionPaint.textSize = context.sp(13f)
        scorePaint.textSize = context.sp(13f)
        deletedPaint.textSize = context.sp(COMMENT_BODY_TEXT_POINTS)
    }

    private fun configureBody(comment: Comment) {
        emojiTextMode = if (comment.isDeleted) EmojiTextMode.NormalText
            else EmojiOnlyClassifier.classify(comment.body, comment.entities)

        val textSizeSp = bodySpForMode(emojiTextMode)
        bodyPaint.textSize = context.sp(textSizeSp)
        bodyText = if (comment.isDeleted) "Commento non disponibile" else withWebLinks(buildCommentBodyText(
            comment = comment,
            emojiSizePx = (bodyPaint.textSize * emojiDpForMode(emojiTextMode) / textSizeSp).roundToInt(),
        ))
    }

    /** Assegna i colori base presi dal tema globale ai vari "pennelli" usati per disegnare. */
    private fun applyColors() {
        authorPaint.color = NativeColors.textPrimary
        metaPaint.color = NativeColors.textSecondary
        bodyPaint.color = NativeColors.textPrimary
        actionPaint.color = NativeColors.interactionInactive
        deletedPaint.color = NativeColors.textSecondary
    }

    /** Decide se le emoji devono muoversi o rimanere ferme (congelate). */
    override fun setEmojiAnimating(value: Boolean) {
        emojiAnimating = value
        mediaView.animationsRunning = value
        if (isAttachedToWindow) {
            emojiRenderer.setAnimating(value)
        }
    }

    override val isAttachedForLottie: Boolean
        get() = isAttachedToWindow

    /** Comunica allo schermo di ridisegnare la zona dove si trova un'emoji. */
    override fun invalidateLottie(bounds: Rect?) {
        if (!isAttachedForLottie) return
        val coordinator = emojiFrameCoordinator
        if (coordinator != null) {
            coordinator.requestInvalidate(this)
        } else {
            invalidate()
        }
    }

    /** Sincronizza le animazioni di tutte le emoji per farle muovere all'unisono. */
    override fun setEmojiFrameCoordinator(coordinator: EmojiFrameCoordinator?) {
        if (emojiFrameCoordinator !== coordinator) {
            emojiFrameCoordinator?.cancel(this)
            emojiFrameCoordinator = coordinator
        }
        emojiRenderer.setFrameCoordinator(coordinator)
    }

    /** Controlla se ci sono emoji in movimento in questo istante. */
    override fun isEmojiPlaying(): Boolean = emojiRenderer.isPlaying

    /** Riempie la View con i dati del commento (testo, utente, click) appena arriva. */
    fun bind(
        item: CommentUiModel,
        onUpvote: (Comment) -> Unit = {},
        onDownvote: (Comment) -> Unit = {},
        onDelete: (Comment) -> Unit = {},
        onMenu: (Comment) -> Unit = {},
        onOpen: (Comment) -> Unit = {},
        onReply: ((Comment) -> Unit)? = null,
        thread: FlattenedComment? = null,
        onAuthor: (String) -> Unit = {},
    ) {
        val comment = item.comment
        val same = this.item?.id == item.id

        this.item = item
        this.onUpvote = onUpvote
        this.onDownvote = onDownvote
        this.onDelete = onDelete
        this.onMenu = onMenu
        this.onOpen = onOpen
        this.onReply = onReply ?: onOpen
        this.thread = thread
        this.onAuthor = onAuthor
        elevation = 0f

        configureBody(comment)
        val media = comment.media.takeUnless { comment.isDeleted }
        val isPersonalSticker = media?.provider == "axoo" && media.type == "sticker"
        mediaView.cornerRadius = if (media?.type == "gif" || isPersonalSticker) points(12f) else 0f
        val url = if (media?.provider == "aup" && android.os.Build.VERSION.SDK_INT < 28) media.previewFallbackUrl ?: media.url else media?.url
        mediaView.bind(url, media?.thumbnailUrl.takeIf { media?.type == "gif" })

        authorText = comment.nickname.trim()
        timeAgo = buildString {
            append(formatTimeAgo(comment.createdAt))
            if (isContentEdited(comment.createdAt, comment.updatedAt)) {
                append(" · Modificato")
            }
        } + when (item.deliveryState) {
            CommentDeliveryState.Sending -> ""
            CommentDeliveryState.Sent -> ""
        }

        scoreAnimation.update(
            nextScore = comment.upvotes - comment.downvotes,
            nextVote = comment.myVote,
            sameItem = same,
        )

        bindAvatar(comment)

        measuredForWidth = 0
        requestLayout()
    }

    /** Aggiorna solo la grafica dei voti senza sprecare risorse per ricostruire il testo o le emoji. */
    fun bindVote(item: CommentUiModel) {
        if (this.item?.stableKey != item.stableKey) return
        val oldWidth = scorePaint.measureText(scoreAnimation.currentScore.toString())
        this.item = item
        scoreAnimation.update(
            nextScore = item.comment.upvotes - item.comment.downvotes,
            nextVote = item.comment.myVote,
            sameItem = true,
        )
        if (oldWidth != scorePaint.measureText(scoreAnimation.currentScore.toString())) {
            measuredForWidth = 0
            requestLayout()
        }
        invalidate()
    }

    /** Aggiorna i dati del server (es. stato di invio) senza ricostruire il testo o le emoji. */
    fun bindMetadata(item: CommentUiModel) {
        val previous = this.item ?: return
        if (!previous.hasSameRenderedBody(item)) return

        val comment = item.comment
        this.item = item
        authorText = comment.nickname.trim()
        timeAgo = buildString {
            append(formatTimeAgo(comment.createdAt))
            if (isContentEdited(comment.createdAt, comment.updatedAt)) {
                append(" · Modificato")
            }
        }
        scoreAnimation.update(
            nextScore = comment.upvotes - comment.downvotes,
            nextVote = comment.myVote,
            sameItem = true,
        )
        bindAvatar(comment)

        measuredForWidth = 0
        requestLayout()
    }

    /** Stacca la spina e spegne le animazioni quando il commento esce dallo schermo. */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        avatarJob?.cancel()
        avatarJob = null
        emojiRenderer.setAnimating(false)
        emojiRenderer.refreshHostAttachment()
    }

    /** Riaccende le animazioni quando il commento torna visibile sullo schermo. */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        emojiRenderer.refreshHostAttachment()
        emojiRenderer.setAnimating(emojiAnimating)
        comment?.let { bindAvatar(it) }
    }

    /** Calcola matematicamente l'altezza e la larghezza del commento in base alla lunghezza del testo. */
    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
            .coerceAtLeast(1)

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
        comment?.let(::configureBody)
        measuredForWidth = 0
        requestLayout()
        invalidate()
    }

    //questa e' la funzione che effettivamente disegna il commento
    override fun onDraw(canvas: Canvas) {
        val comment = comment ?: return
        ensureLayout(width)
        applyColors()

        bgPaint.color = NativePillDefaults.backgroundColor
        val cardRadius = points(8f)
        if (thread == null) {
            canvas.drawRoundRect(cardRect, cardRadius, cardRadius, bgPaint)
            canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardStrokePaint)
        }
        drawThreadLines(canvas)
        if (comment.isDeleted) {
            canvas.drawText("Commento non disponibile", bodyLeft, bodyTop - deletedPaint.ascent(), deletedPaint)
            return
        }

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
            val fallbackR = avatarRect.width() * 0.3f
            canvas.drawCircle(avatarRect.centerX(), avatarRect.centerY(), fallbackR, avatarPaint)
        }

        canvas.drawText(
            authorText,
            authorLeft,
            authorBaseline,
            authorPaint,
        )
        if (isVerifiedAvatar(comment?.avatarConfig)) {
            val badgeLeft = authorLeft + authorPaint.measureText(authorText) + points(3f)
            val badgeTop = authorBaseline + (authorPaint.ascent() + authorPaint.descent()) / 2f - points(8f)
            tmpRect.set(badgeLeft, badgeTop, badgeLeft + points(16f), badgeTop + points(16f))
            drawIcon(canvas, NativeIcons.VERIFIED, tmpRect, Color.rgb(34, 139, 230))
        }

        canvas.drawText(
            metaText,
            metaLeft,
            metaBaseline,
            metaPaint,
        )

        canvas.save()
        canvas.translate(bodyLeft, bodyTop)
        // Tecnica Telegram: allinea le emoji animate al testo conservando la loro ultima
        // posizione valida finché il Layout non le ridisegna sullo schermo.
        emojiRenderer.beforeTextDraw(bodyLayout, bodyText)

        if (bodyText.isNotEmpty()) bodyLayout?.draw(canvas)
        emojiRenderer.draw(canvas)

        canvas.restore()

        val inactiveColor = NativeColors.interactionInactive
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

        val iconSize = points(24f)

        val rowCenterY = upvoteRect.centerY()
        val iconTopY = rowCenterY - iconSize / 2f

        tmpRect.set(
            menuRect.centerX() - iconSize / 2f,
            menuRect.centerY() - iconSize / 2f,
            menuRect.centerX() + iconSize / 2f,
            menuRect.centerY() + iconSize / 2f,
        )

        drawIcon(canvas, NativeIcons.MENU, tmpRect, inactiveColor)
        canvas.drawText("Rispondi", replyRect.left,
            replyRect.centerY() - (actionPaint.ascent() + actionPaint.descent()) / 2f, actionPaint)

        iconPaint.style = Paint.Style.STROKE

        // Bring the icons toward the score without shrinking their touch targets.
        val voteIconInset = points(4f)
        tmpRect.set(
            upvoteRect.centerX() + voteIconInset - iconSize / 2f,
            iconTopY,
            upvoteRect.centerX() + voteIconInset + iconSize / 2f,
            iconTopY + iconSize,
        )

        drawIcon(canvas, NativeIcons.COMMENT_UP, tmpRect, upColor)

        tmpRect.set(
            downvoteRect.centerX() - voteIconInset - iconSize / 2f,
            iconTopY,
            downvoteRect.centerX() - voteIconInset + iconSize / 2f,
            iconTopY + iconSize,
        )

        drawIcon(canvas, NativeIcons.COMMENT_DOWN, tmpRect, downColor)

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
            left = upvoteRect.right,
            right = downvoteRect.left,
            centerY = rowCenterY,
            halfHeightPx = points(12f),
            gapPx = points(2f),
        )
    }

    private fun drawThreadLines(canvas: Canvas) {
        val row = thread ?: return
        threadPath.rewind()
        val corner = points(6f)
        val branchY = avatarRect.centerY()
        for (depth in 1..MAX_COMMENT_INDENT) {
            val above = depth in row.linesAbove
            val below = depth in row.linesBelow
            if (!above && !below) continue
            val x = cardRect.left + points(18f + (depth - 1) * COMMENT_INDENT_DP)
            if (above) {
                if (depth == row.depth) {
                    // Rounded elbow into this child's avatar; the trunk continues
                    // only when a later sibling still needs it.
                    threadPath.moveTo(x, 0f)
                    threadPath.lineTo(x, branchY - corner)
                    threadPath.quadTo(x, branchY, x + corner, branchY)
                    threadPath.lineTo(avatarRect.left - points(3f), branchY)
                    if (below) {
                        threadPath.moveTo(x, branchY - corner)
                        threadPath.lineTo(x, height.toFloat())
                    }
                } else {
                    threadPath.moveTo(x, 0f)
                    threadPath.lineTo(x, height.toFloat())
                }
            } else if (below) {
                // Start below the parent's actions, without crossing its text.
                threadPath.moveTo(x, cardRect.bottom - points(8f))
                threadPath.lineTo(x, height.toFloat())
            }
        }
        threadPaint.color = NativeColors.textSecondary.withAlpha(0x55)
        canvas.drawPath(threadPath, threadPaint)
    }

    /** Capisce matematicamente cosa hai toccato col dito (es. il bottone Like, l'Avatar o il Testo). */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val comment = comment ?: return false
        if (comment.isDeleted || item?.isOptimistic == true) return true
        if (webLinks.onTouch(event, bodyLayout, bodyLeft, bodyTop)) return true
        if (event.action != MotionEvent.ACTION_UP) return true

        val x = event.x
        val y = event.y

        when {
            mediaRect.contains(x, y) && comment.media?.provider == "axoo" && comment.media.type == "sticker" ->
                com.axoo.features.posts.presentation.stickers.StickerActions.show(this, comment.media)
            avatarRect.contains(x, y) || authorRect.contains(x, y) -> onAuthor?.invoke(comment.userId)
            replyRect.contains(x, y) -> onReply?.invoke(comment)
            upvoteRect.contains(x, y) ||
                    (
                            x >= upvoteRect.left - points(6f) &&
                                    x <= upvoteRect.right + points(14f) &&
                                    y >= upvoteRect.top - points(6f) &&
                                    y <= upvoteRect.bottom + points(6f)
                            ) -> {
                Haptics.click(this)
                onUpvote?.invoke(comment)
            }

            downvoteRect.contains(x, y) ||
                    (
                            x >= downvoteRect.left - points(6f) &&
                                    x <= downvoteRect.right + points(14f) &&
                                    y >= downvoteRect.top - points(6f) &&
                                    y <= downvoteRect.bottom + points(6f)
                            ) -> {
                Haptics.click(this)
                onDownvote?.invoke(comment)
            }

            menuRect.contains(x, y) ||
                    (
                            x >= menuRect.left - points(8f) &&
                                    x <= menuRect.right + points(8f) &&
                                    y >= menuRect.top - points(8f) &&
                                    y <= menuRect.bottom + points(8f)
                            ) -> {
                onMenu?.invoke(comment)
            }

            else -> {
                performClick()
                onOpen?.invoke(comment)
            }

        }

        return true
    }

    /** Gestisce il tocco standard di Android. */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** Calcola la posizione precisa di ogni singola parola e icona prima di disegnarla. */
    private fun ensureLayout(width: Int) {
        if (width == measuredForWidth && bodyLayout != null) return

        measuredForWidth = width
        val nextScale = context.cardPixelsPerPoint(width)
        if (pixelsPerPoint != nextScale) {
            pixelsPerPoint = nextScale
            configureTextSizes()
            comment?.let(::configureBody)
            threadPaint.strokeWidth = points(1f)
            cardStrokePaint.strokeWidth = points(NativePillDefaults.borderWidthDp)
            elevation = 0f
            elevation = 0f
        }

        val hMargin = points(16f)
        val vMargin = points(4f)
        val cardLeft = hMargin
        val cardRight = width - hMargin
        val cardWidth = (cardRight - cardLeft).coerceAtLeast(1f)
        val top = if (thread?.isFirst != false) vMargin else 0f
        val bottomMargin = if (thread?.isLast != false) vMargin else 0f
        val indent = points(COMMENT_INDENT_DP) * (thread?.depth ?: 0).coerceAtMost(MAX_COMMENT_INDENT)
        val contentLeft = cardLeft + points(14f) + indent

        val headerTop = top + points(12f)
        authorBaseline = headerTop - authorPaint.ascent()
        metaBaseline = headerTop - metaPaint.ascent()
        bodyLeft = contentLeft
        bodyTop = if (comment?.isDeleted == true) headerTop
            else headerTop + maxOf(points(28f), authorPaint.fontMetrics.descent - authorPaint.fontMetrics.ascent) + points(8f)

        val avatarSize = points(28f)

        val bodyWidthPx = (cardWidth - points(28f) - indent)
            .toInt()
            .coerceAtLeast(1)

        bodyWidth = bodyWidthPx

        bodyLayout = bodyPaint.staticLayout(
            text = bodyText,
            width = bodyWidthPx,
            lineSpacingMultiplier = 1f,
        )

        emojiRenderer.updateFromText(bodyText)
        emojiRenderer.setAnimating(emojiAnimating && isAttachedToWindow)

        val bodyH = if (bodyText.isEmpty()) 0 else bodyLayout?.height ?: 0
        val media = comment?.media.takeUnless { comment?.isDeleted == true }
        if (media != null) {
            val size = postMediaViewportSize(bodyWidthPx, points(200f).toInt(), media.width, media.height)!!
            val mediaTop = bodyTop + bodyH + if (bodyH > 0) points(8f) else 0f
            val mediaLeft = bodyLeft + (bodyWidthPx - size.width) / 2f
            mediaRect.set(mediaLeft, mediaTop, mediaLeft + size.width, mediaTop + size.height)
        } else mediaRect.setEmpty()
        val actionsHeight = maxOf(points(44f), actionPaint.descent() - actionPaint.ascent() + points(8f),
            scorePaint.descent() - scorePaint.ascent() + points(8f))
        val actionsTop = (if (mediaRect.isEmpty) bodyTop + bodyH else mediaRect.bottom) + points(8f)
        val voteWidth = points(88f) + maxOf(points(48f), scorePaint.measureText(scoreAnimation.currentScore.toString()) + points(8f))
        val replyWidth = minOf(bodyWidth.toFloat(),
            maxOf(points(90f), actionPaint.measureText("Rispondi") + points(8f))).coerceAtLeast(1f)
        val separateActions = bodyWidth < replyWidth + voteWidth + points(8f)
        val votesTop = actionsTop + if (separateActions) actionsHeight else 0f
        val totalH = if (comment?.isDeleted == true) actionsTop + points(8f)
            else votesTop + actionsHeight + points(8f)

        measuredHeightPx = kotlin.math.ceil((totalH + bottomMargin).toDouble()).toInt()

        cardRect.set(cardLeft, top, cardRight, totalH)
        invalidateOutline()

        avatarRect.set(
            contentLeft,
            top + points(12f),
            contentLeft + avatarSize,
            top + points(12f) + avatarSize,
        )

        avatarClipPath.reset()
        avatarClipPath.addOval(avatarRect, Path.Direction.CW)

        authorLeft = contentLeft + avatarSize + points(8f)
        val headerRight = cardRight - points(58f)
        val rawAuthor = comment?.nickname.orEmpty().trim().ifEmpty { "Anonimo" }
        val badgeWidth = if (isVerifiedAvatar(comment?.avatarConfig)) points(20f) else 0f
        val authorGap = if (rawAuthor.isEmpty()) 0f else points(6f)
        val headerWidth = (headerRight - authorLeft).coerceAtLeast(0f)
        metaText = TextUtils.ellipsize(
            buildString {
                if (rawAuthor.isNotEmpty()) append("· ")
                comment?.universityName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    append(it).append(" · ")
                }
                append(timeAgo)
            },
            metaPaint,
            (headerWidth - points(32f) - authorGap - badgeWidth).coerceAtLeast(0f),
            TextUtils.TruncateAt.END,
        ).toString()
        val maxAuthorWidth = (
                headerRight - authorLeft - metaPaint.measureText(metaText) - authorGap - badgeWidth
                ).coerceAtLeast(points(32f))
        authorText = TextUtils.ellipsize(
            rawAuthor,
            authorPaint,
            maxAuthorWidth,
            TextUtils.TruncateAt.END,
        ).toString()
        metaLeft = authorLeft + authorPaint.measureText(authorText) + badgeWidth + authorGap
        authorRect.set(authorLeft, avatarRect.top, metaLeft - authorGap, avatarRect.bottom)

        val touchSize = points(44f)
        val menuTop = headerTop + (authorPaint.descent() - authorPaint.ascent() - touchSize) / 2f
        menuRect.set(cardRight - points(58f), menuTop,
            cardRight - points(14f), menuTop + touchSize)
        replyRect.set(contentLeft, actionsTop, contentLeft + replyWidth, actionsTop + actionsHeight)
        val voteLeft = cardRight - points(14f) - voteWidth
        upvoteRect.set(voteLeft, votesTop, voteLeft + touchSize, votesTop + actionsHeight)
        downvoteRect.set(cardRight - points(58f), votesTop,
            cardRight - points(14f), votesTop + actionsHeight)
    }

    /** Gestisce la transizione e l'animazione fluida quando i numeri dei voti cambiano. */

    /** Restituisce il colore corretto per la freccia in sù. */
    private fun voteUpColor(vote: VoteState): Int =
        if (vote == VoteState.Upvote) NativeColors.accent else NativeColors.interactionInactive

    /** Restituisce il colore corretto per la freccia in giù. */
    private fun voteDownColor(vote: VoteState): Int =
        if (vote == VoteState.Downvote) NativeColors.downvoteActive else NativeColors.interactionInactive

    /** Decide il colore del numero dei voti (magenta, celeste o grigio). */
    private fun voteScoreColor(vote: VoteState): Int = when (vote) {
        VoteState.Upvote -> NativeColors.accent
        VoteState.Downvote -> NativeColors.downvoteActive
        VoteState.None -> NativeColors.textPrimary
    }

    /** Fonde due colori matematicamente per creare sfumature fluide nelle animazioni. */
    private fun blendColor(from: Int, to: Int, progress: Float): Int {
        val t = progress.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t).roundToInt(),
            (Color.red(from) + (Color.red(to) - Color.red(from)) * t).roundToInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * t).roundToInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).roundToInt(),
        )
    }

    /** Scarica e imposta l'immagine del profilo dell'autore del commento. */
    private fun bindAvatar(comment: Comment) {
        if (comment.isDeleted) {
            avatarJob?.cancel()
            avatarUrl = null
            avatarBitmap = null
            return
        }
        val nextUrl = buildAvatarUrl(comment.userId, comment.avatarConfig)
        val sizePx = points(84f).toInt()

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

    /** Calcola la grandezza delle emoji in base a quante ce ne sono (1 enorme, 5 piccole). */
    private fun emojiDpForMode(mode: EmojiTextMode): Float = when (mode) {
        EmojiTextMode.OneEmoji -> 64f
        EmojiTextMode.TwoEmoji -> 56f
        EmojiTextMode.ThreeEmoji -> 48f
        EmojiTextMode.FourEmoji -> 40f
        EmojiTextMode.FiveEmoji -> 32f
        EmojiTextMode.NormalText -> COMMENT_BODY_TEXT_POINTS * MIXED_TEXT_EMOJI_SCALE
    }

    /** Calcola la grandezza del testo standard del commento. */
    private fun bodySpForMode(mode: EmojiTextMode): Float = when (mode) {
        EmojiTextMode.OneEmoji -> 56f
        EmojiTextMode.TwoEmoji -> 50f
        EmojiTextMode.ThreeEmoji -> 42f
        EmojiTextMode.FourEmoji -> 34f
        EmojiTextMode.FiveEmoji -> 28f
        EmojiTextMode.NormalText -> COMMENT_BODY_TEXT_POINTS
    }

    /** Svuota completamente il commento per poter riciclare l'oggetto e risparmiare RAM. */
    fun recycle() {
        scoreAnimation.cancel()
        avatarJob?.cancel()
        avatarJob = null
        avatarUrl = null
        avatarBitmap = null
        item = null
        onAuthor = null
        thread = null
        bodyLayout = null
        bodyText = ""
        authorText = ""
        metaText = ""
        measuredForWidth = 0
        emojiRenderer.clear()
        invalidate()
    }

    /** Disegna un'icona (es. i 3 puntini del menù o le frecce) sulla tela. */
    private fun drawIcon(
        canvas: Canvas,
        name: String,
        rect: RectF,
        color: Int,
    ) {
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

private const val COMMENT_BODY_TEXT_POINTS = 16f

internal fun buildCommentBodyText(
    comment: Comment,
    emojiSizePx: Int,
): CharSequence {
    if (comment.entities.isEmpty()) {
        return SpannableStringBuilder(comment.body.replace(whitespaceRegex, " ").trim())
    }

    val builder = SpannableStringBuilder()
    var cursor = 0

    comment.entities
        .filter { it.type == MessageEntity.TYPE_CUSTOM_EMOJI || it.type == MessageEntity.TYPE_STATIC_EMOJI }
        .sortedBy { it.offset }
        .forEach { entity ->
            val start = entity.offset
            val end = start + entity.length

            if (start < cursor || start < 0 || end > comment.body.length) {
                return@forEach
            }

            if (start > cursor) {
                builder.append(comment.body.substring(cursor, start))
            }

            if (entity.type == MessageEntity.TYPE_STATIC_EMOJI) {
                builder.append(comment.body.substring(start, end))
                cursor = end
                return@forEach
            }

            val emoji = EmojiData.byId[entity.customEmojiId]

            if (emoji == null) {
                if (com.axoo.BuildConfig.DEBUG) {
                    Log.d(
                        EMOJI_RENDER_TAG,
                        "missing_custom_emoji_id id=${entity.customEmojiId} commentId=${comment.id}"
                    )
                }
                builder.append(comment.body.substring(start, end))
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
                    fallbackChar = emoji.fallback.ifBlank {
                        comment.body.substring(start, end)
                    },
                ),
                spanStart,
                spanStart + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )

            cursor = end
        }

    if (cursor < comment.body.length) {
        builder.append(comment.body.substring(cursor))
    }

    return builder
}
