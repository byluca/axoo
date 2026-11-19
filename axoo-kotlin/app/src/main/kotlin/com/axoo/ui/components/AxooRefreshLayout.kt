package com.axoo.ui.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import com.axoo.core.haptics.Haptics
import com.axoo.ui.theme.dp
import kotlin.math.abs
import kotlin.math.sin

/**
 * Lightweight pull-to-refresh used by the home feeds.
 *
 * The content itself moves down, revealing the Axoo accent surface between the app bar and the
 * first post. The indicator is drawn behind the content, so it is progressively uncovered by the
 * gesture without requiring a Lottie asset.
 */
internal class AxooRefreshLayout(
    context: Context,
    private val content: View,
    private val canChildScrollUp: () -> Boolean,
) : FrameLayout(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val settleHeight = dp(60f)
    private val maxPull = dp(112f)
    private val spinnerSize = dp(48f).toInt()
    private var downX = 0f
    private var downY = 0f
    private var pullOffset = 0f
    private var dragging = false
    private var refreshing = false
    private var closing = false
    private var refreshListener: () -> Unit = {}

    private val spinner = AxooRefreshSpinnerView(context)

    init {
        clipChildren = true
        setBackgroundColor(0xFFFF00BF.toInt())

        addView(
            spinner,
            LayoutParams(spinnerSize, spinnerSize).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                topMargin = ((settleHeight - spinnerSize) / 2f).toInt()
            },
        )
        addView(
            content,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    var isRefreshing: Boolean
        get() = refreshing
        set(value) {
            if (refreshing == value) return
            refreshing = value
            content.animate().cancel()
            closing = false
            if (value) {
                spinner.startSpinning()
                animateContentTo(settleHeight)
            } else {
                animateContentTo(0f, hideSpinnerAtEnd = true)
            }
        }

    fun setOnRefreshListener(listener: () -> Unit) {
        refreshListener = listener
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (refreshing || closing || canChildScrollUp()) return false
                val dx = event.x - downX
                val dy = event.y - downY
                if (dy > touchSlop && dy > abs(dx)) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (refreshing) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                updatePull(event.y - downY - touchSlop)
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!dragging) return false
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (pullOffset >= settleHeight) {
                    refreshing = true
                    Haptics.refresh(this)
                    spinner.startSpinning()
                    animateContentTo(settleHeight)
                    refreshListener()
                } else {
                    animateContentTo(0f, hideSpinnerAtEnd = true)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                animateContentTo(0f, hideSpinnerAtEnd = true)
                return true
            }
        }
        return true
    }

    private fun updatePull(rawDistance: Float) {
        val resistedDistance = rawDistance.coerceAtLeast(0f) * DRAG_RESISTANCE
        pullOffset = if (resistedDistance <= settleHeight) {
            resistedDistance
        } else {
            settleHeight + (resistedDistance - settleHeight) * OVERSHOOT_RESISTANCE
        }.coerceAtMost(maxPull)
        content.translationY = pullOffset
        spinner.setPullProgress((pullOffset / settleHeight).coerceIn(0f, 1f))
    }

    private fun animateContentTo(target: Float, hideSpinnerAtEnd: Boolean = false) {
        pullOffset = target
        closing = hideSpinnerAtEnd
        content.animate()
            .translationY(target)
            .setDuration(300L)
            .setInterpolator(com.axoo.core.anim.AxooInterpolators.Decelerate)
            .setListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        closing = false
                        if (hideSpinnerAtEnd && !refreshing) finishClosedState()
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        closing = false
                        if (hideSpinnerAtEnd && !refreshing) finishClosedState()
                    }
                },
            )
            .start()
    }

    private fun finishClosedState() {
        pullOffset = 0f
        content.translationY = 0f
        spinner.stopAndHide()
    }

    override fun onDetachedFromWindow() {
        content.animate().cancel()
        closing = false
        spinner.stopAndHide()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (refreshing) {
            content.translationY = settleHeight
            spinner.startSpinning()
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        const val DRAG_RESISTANCE = 0.72f
        const val OVERSHOOT_RESISTANCE = 0.34f
        const val SETTLE_DURATION_MS = 220L
    }
}

/** A tiny Axoo face with six animated gills, drawn without bitmap or Lottie assets. */
private class AxooRefreshSpinnerView(context: Context) : View(context) {
    private val mascotPainter = AxooMascotCanvasPainter()
    private val mascotBounds = RectF()
    private var pullProgress = 0f
    private var rotationDegrees = 0f
    private var animationPhase = 0f
    private var animator: ValueAnimator? = null

    init {
        visibility = INVISIBLE
        alpha = 0f
    }

    fun setPullProgress(progress: Float) {
        if (animator != null) return
        pullProgress = progress.coerceIn(0f, 1f)
        visibility = if (pullProgress > 0f) VISIBLE else INVISIBLE
        alpha = ((pullProgress - 0.08f) / 0.52f).coerceIn(0f, 1f)
        scaleX = 0.72f + pullProgress * 0.28f
        scaleY = scaleX
        // Complete exactly one turn at the refresh threshold, so while the user holds the pull
        // before releasing, the mascot is upright rather than left upside down.
        rotationDegrees = pullProgress * 360f
        invalidate()
    }

    fun startSpinning() {
        visibility = VISIBLE
        alpha = 1f
        scaleX = 1f
        scaleY = 1f
        if (animator != null) return
        val startRotation = rotationDegrees
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SPIN_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = com.axoo.core.anim.AxooInterpolators.Linear
            addUpdateListener {
                animationPhase = it.animatedValue as Float
                val elasticOffset = sin(animationPhase * FULL_TURN_RADIANS) * WOBBLE_DEGREES
                rotationDegrees = startRotation + animationPhase * 360f + elasticOffset
                invalidate()
            }
            start()
        }
    }

    fun stopAndHide() {
        animator?.cancel()
        animator = null
        pullProgress = 0f
        rotationDegrees = 0f
        animationPhase = 0f
        alpha = 0f
        visibility = INVISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val wave = if (animator != null) {
            sin(animationPhase * FULL_TURN_RADIANS)
        } else {
            sin(pullProgress * Math.PI).toFloat()
        }
        val verticalOffset = if (animator != null) wave * resources.displayMetrics.density * 0.45f else 0f
        mascotBounds.set(
            0f,
            verticalOffset,
            width.toFloat(),
            height.toFloat() + verticalOffset,
        )
        canvas.save()
        canvas.rotate(rotationDegrees, width / 2f, height / 2f)
        mascotPainter.draw(
            canvas = canvas,
            bounds = mascotBounds,
            gillPhaseDegrees = null,
            squash = wave,
        )
        canvas.restore()
    }

    private companion object {
        const val SPIN_DURATION_MS = 760L
        const val FULL_TURN_RADIANS = (Math.PI * 2.0).toFloat()
        const val WOBBLE_DEGREES = 5.5f
    }
}
