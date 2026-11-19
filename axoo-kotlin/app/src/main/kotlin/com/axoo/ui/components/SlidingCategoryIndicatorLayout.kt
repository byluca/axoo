package com.axoo.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.ViewGroup
import android.widget.LinearLayout
import com.axoo.ui.theme.NativeColors

internal class SlidingCategoryIndicatorLayout(
    context: Context,
    private val indicatorWidthPx: Int,
    private val indicatorHeightPx: Int,
    private val indicatorCornerRadiusPx: Float,
    private val selectionContainer: (() -> ViewGroup?)? = null,
    private val indicatorHorizontalOutsetPx: Int = 0,
) : LinearLayout(context) {
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NativeColors.categorySelection
    }
    private val indicatorRect = RectF()
    private val selectionBounds = Rect()
    private var selectedIndex = -1
    private var indicatorLeft = 0f
    private var currentIndicatorWidth = 0f
    private var hasIndicatorPosition = false
    private var pendingSelectionUpdate = false
    private var animatePendingUpdate = false
    private var indicatorAnimator: ValueAnimator? = null

    init {
        setWillNotDraw(false)
    }

    fun setSelectedIndex(index: Int, animate: Boolean = true) {
        val selectionChanged = selectedIndex != index
        if (!selectionChanged && (hasIndicatorPosition || pendingSelectionUpdate)) return

        selectedIndex = index
        pendingSelectionUpdate = true
        animatePendingUpdate = selectionChanged && animate && hasIndicatorPosition

        if (isLaidOut && !isLayoutRequested) {
            applyPendingSelection()
        }
    }

    fun setIndicatorColor(color: Int) {
        if (indicatorPaint.color == color) return
        indicatorPaint.color = color
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (pendingSelectionUpdate) {
            applyPendingSelection()
        } else if (indicatorAnimator?.isRunning != true) {
            moveIndicatorToSelection(animate = false)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (hasIndicatorPosition && selectedIndex in 0 until (selectionContainer?.invoke() ?: this).childCount) {
            val indicatorTop = (height - indicatorHeightPx) / 2f
            indicatorRect.set(
                indicatorLeft,
                indicatorTop,
                indicatorLeft + currentIndicatorWidth,
                indicatorTop + indicatorHeightPx,
            )
            canvas.drawRoundRect(
                indicatorRect,
                indicatorCornerRadiusPx,
                indicatorCornerRadiusPx,
                indicatorPaint,
            )
        }
        super.dispatchDraw(canvas)
    }

    override fun onDetachedFromWindow() {
        indicatorAnimator?.cancel()
        indicatorAnimator = null
        super.onDetachedFromWindow()
    }

    private fun applyPendingSelection() {
        val animate = animatePendingUpdate
        pendingSelectionUpdate = false
        animatePendingUpdate = false
        moveIndicatorToSelection(animate)
    }

    private fun moveIndicatorToSelection(animate: Boolean) {
        val selectedChild = (selectionContainer?.invoke() ?: this).getChildAt(selectedIndex)
        if (selectedChild == null || selectedChild.width == 0) {
            pendingSelectionUpdate = true
            animatePendingUpdate = animate
            invalidate()
            return
        }

        // Descendant targets let native navigation reuse the category animation.
        selectedChild.getDrawingRect(selectionBounds)
        offsetDescendantRectToMyCoords(selectedChild, selectionBounds)
        val targetWidth = if (indicatorWidthPx > 0) {
            indicatorWidthPx.toFloat()
        } else {
            (selectedChild.width + indicatorHorizontalOutsetPx * 2).toFloat()
        }
        val targetLeft = selectionBounds.left + (selectedChild.width - targetWidth) / 2f

        indicatorAnimator?.cancel()

        if (!animate || !hasIndicatorPosition) {
            indicatorLeft = targetLeft
            currentIndicatorWidth = targetWidth
            hasIndicatorPosition = true
            invalidate()
            return
        }

        val startLeft = indicatorLeft
        val startWidth = currentIndicatorWidth
        indicatorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = INDICATOR_ANIMATION_DURATION_MS
            interpolator = com.axoo.core.anim.AxooInterpolators.Decelerate
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                indicatorLeft = startLeft + (targetLeft - startLeft) * fraction
                currentIndicatorWidth = startWidth + (targetWidth - startWidth) * fraction
                invalidate()
            }
            start()
        }
    }

    private companion object {
        const val INDICATOR_ANIMATION_DURATION_MS = 220L
    }
}
