package com.axoo.core.anim

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.view.ViewCompat

enum class ScreenTransition {
    None,
    Push,
    Pop,
    Replace,
    Present,
    Dismiss,
}

/** Il navigatore possiede le pagine; qui gestiamo soltanto posizione e aspetto. */
class SlidePageAnimator(private val container: FrameLayout) {
    private val slideInterpolator = PathInterpolator(0.22f, 0.92f, 0.18f, 1f)
    private val fadeInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    private val presentInterpolator = PathInterpolator(0.2f, 0f, 0.2f, 1f)
    private val scrim = View(container.context).apply {
        setBackgroundColor(Color.BLACK)
        isClickable = false
    }
    private val scrimBounds = Rect()
    private val cornerRadius = 24f * container.resources.displayMetrics.density
    private val pageOutline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
        }
    }
    private var animator: ValueAnimator? = null
    private var oldPage: View? = null
    private var newPage: View? = null
    private var transition = ScreenTransition.None
    private var progress = 0f
    private var width = 1f
    private var height = 1f

    val isTransitionInProgress: Boolean get() = newPage != null
    var isInteractivePopInProgress = false
        private set

    val currentTopView: View?
        get() = newPage ?: (container.childCount - 1 downTo 0)
            .firstOrNull { container.getChildAt(it) !== scrim }
            ?.let(container::getChildAt)

    fun replace(
        oldView: View?,
        newView: View,
        transition: ScreenTransition,
        onMounted: () -> Unit,
        onFinished: () -> Unit = {},
    ) {
        cancelCurrentTransition()
        if (oldView == null || oldView === newView || transition == ScreenTransition.None) {
            oldView?.let(::resetPage)
            resetPage(newView)
            attach(newView)
            container.bringChildToFront(newView)
            onMounted()
            requestInsets(newView)
            onFinished()
            return
        }

        prepare(oldView, newView, transition)
        onMounted()
        val duration = when (transition) {
            ScreenTransition.Present -> 400L
            ScreenTransition.Push -> 280L
            ScreenTransition.Pop, ScreenTransition.Dismiss -> 260L
            else -> 140L
        }
        animateTo(1f, duration, newView, onFinished)
    }

    fun beginInteractivePop(currentView: View, previousView: View) {
        cancelCurrentTransition()
        prepare(currentView, previousView, ScreenTransition.Pop)
        isInteractivePopInProgress = true
    }

    fun updateInteractivePop(dx: Float) {
        if (!isInteractivePopInProgress || animator != null) return
        applyProgress(dx / width)
    }

    fun cancelInteractivePop(onCancelled: () -> Unit = {}) {
        val current = oldPage ?: return onCancelled()
        animateTo(0f, (180f * progress).toLong().coerceAtLeast(80L), current, onCancelled)
    }

    fun finishInteractivePop(onFinished: () -> Unit = {}) {
        val previous = newPage ?: return onFinished()
        animateTo(1f, (220f * (1f - progress)).toLong().coerceAtLeast(80L), previous, onFinished)
    }

    private fun prepare(oldView: View, newView: View, kind: ScreenTransition) {
        oldPage = oldView
        newPage = newView
        transition = kind
        width = (container.width.takeIf { it > 0 }
            ?: container.resources.displayMetrics.widthPixels).coerceAtLeast(1).toFloat()
        height = (container.height.takeIf { it > 0 }
            ?: container.resources.displayMetrics.heightPixels).coerceAtLeast(1).toFloat()
        resetPage(oldView)
        resetPage(newView)
        oldView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        newView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        attach(newView)

        val closing = kind == ScreenTransition.Pop || kind == ScreenTransition.Dismiss
        val front = if (closing) oldView else newView
        val back = if (closing) newView else oldView
        container.bringChildToFront(back)
        if (kind != ScreenTransition.Replace) {
            front.outlineProvider = pageOutline
            attach(scrim)
            container.bringChildToFront(scrim)
        }
        container.bringChildToFront(front)
        applyProgress(0f)
    }

    /** Un solo avanzamento pilota pagina, parallasse e velo, anche durante lo swipe. */
    private fun applyProgress(value: Float) {
        val old = oldPage ?: return
        val new = newPage ?: return
        progress = value.coerceIn(0f, 1f)
        if (transition == ScreenTransition.Replace) {
            old.alpha = 1f - progress
            new.alpha = progress
            return
        }

        val vertical = transition == ScreenTransition.Present || transition == ScreenTransition.Dismiss
        val opening = transition == ScreenTransition.Push || transition == ScreenTransition.Present
        val front = if (opening) new else old
        val back = if (opening) old else new
        val revealed = if (opening) 1f - progress else progress
        if (vertical) front.translationY = height * revealed else front.translationX = width * revealed
        front.clipToOutline = revealed > 0f
        if (!vertical) back.translationX = -width * PARALLAX * (1f - revealed)
        // Il velo copre anche la pagina visibile attraverso gli angoli arrotondati.
        scrim.alpha = (120f / 255f) * (1f - revealed).coerceAtMost(0.8f)
        if (vertical) {
            scrimBounds.set(0, 0, container.width, (front.translationY + cornerRadius).toInt())
        } else {
            scrimBounds.set(0, 0, (front.translationX + cornerRadius).toInt(), container.height)
        }
        scrim.clipBounds = scrimBounds
    }

    private fun animateTo(target: Float, duration: Long, topOnFinish: View, onEnd: () -> Unit) {
        stopAnimator()
        val next = ValueAnimator.ofFloat(progress, target).apply {
            this.duration = duration
            interpolator = when (transition) {
                ScreenTransition.Replace -> fadeInterpolator
                ScreenTransition.Present -> presentInterpolator
                ScreenTransition.Dismiss -> AxooInterpolators.Exit
                else -> slideInterpolator
            }
            addUpdateListener { applyProgress(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator !== animation) return
                    finish(topOnFinish)
                    onEnd()
                }
            })
        }
        animator = next
        next.start()
    }

    private fun cancelCurrentTransition() {
        stopAnimator()
        newPage?.let(::finish)
    }

    private fun stopAnimator() {
        animator?.apply {
            removeAllListeners()
            removeAllUpdateListeners()
            cancel()
        }
        animator = null
    }

    private fun finish(top: View) {
        stopAnimator()
        container.removeView(scrim)
        oldPage?.let(::resetPage)
        newPage?.let(::resetPage)
        container.bringChildToFront(top)
        oldPage = null
        newPage = null
        transition = ScreenTransition.None
        isInteractivePopInProgress = false
        requestInsets(top)
    }

    private fun resetPage(view: View) {
        view.animate().cancel()
        if (view.outlineProvider === pageOutline) {
            view.clipToOutline = false
            view.outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        view.translationX = 0f
        view.translationY = 0f
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
        view.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    private fun attach(view: View) {
        if (view.parent === container) return
        (view.parent as? ViewGroup)?.removeView(view)
        container.addView(view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
    }

    private fun requestInsets(view: View) {
        ViewCompat.requestApplyInsets(view)
        ViewCompat.requestApplyInsets(container)
    }

    private companion object {
        const val PARALLAX = 0.18f
    }
}
