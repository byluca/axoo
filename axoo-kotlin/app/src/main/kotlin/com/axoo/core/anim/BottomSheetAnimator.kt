/*
 * IL MOTORE DEL PALCOSCENICO (BottomSheetAnimator)
 * Questa classe è "l'operaio" che fa il lavoro sporco.
 * - NON decide lo stile dell'animazione (per quello legge lo spartito AxooInterpolators).
 * - Sposta fisicamente i pixel ascoltando il "Metronomo" di Android (ValueAnimator).
 * - Mantiene una sua memoria (lo 'stato') per ricordarsi se un'animazione è già in corso e, nel caso, bloccarla.
 */
package com.axoo.core.anim

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import kotlin.math.abs

class BottomSheetAnimator {
    private var animator: ValueAnimator? = null

    /** Tira su il menu dal fondo dello schermo. Usa lo spartito "Enter". */
    fun show(
        view: View,
        hiddenOffset: Float,
        onEnd: (() -> Unit)? = null,
    ) {
        animate(
            view = view,
            targetTranslationY = 0f,
            hiddenOffset = hiddenOffset,
            motionInterpolator = AxooInterpolators.Enter,
            onEnd = onEnd,
        )
    }

    /** Spinge giù il menu per nasconderlo. Usa lo spartito "Exit". */
    fun hide(
        view: View,
        hiddenOffset: Float,
        onEnd: (() -> Unit)? = null,
    ) {
        animate(
            view = view,
            targetTranslationY = hiddenOffset.coerceAtLeast(0f),
            hiddenOffset = hiddenOffset,
            motionInterpolator = AxooInterpolators.Exit,
            onEnd = onEnd,
        )
    }

    /** Aumenta fisicamente l'altezza della tendina. */
    fun showHeight(
        view: View,
        targetHeight: Int,
        onEnd: (() -> Unit)? = null,
    ) {
        animateHeight(
            view = view,
            targetHeight = targetHeight.coerceAtLeast(0),
            fullHeight = targetHeight,
            motionInterpolator = AxooInterpolators.Enter,
            onEnd = onEnd,
        )
    }

    /** Schiaccia l'altezza della tendina (es. quando la tastiera scompare). */
    fun hideHeight(
        view: View,
        fullHeight: Int,
        collapsedHeight: Int = 0,
        onEnd: (() -> Unit)? = null,
    ) {
        animateHeight(
            view = view,
            targetHeight = collapsedHeight.coerceAtLeast(0),
            fullHeight = fullHeight,
            motionInterpolator = AxooInterpolators.Exit,
            onEnd = onEnd,
        )
    }

    /** Frena bruscamente: spegne il metronomo se l'utente clicca prima della fine. */
    fun cancel() {
        animator?.cancel()
        animator = null
    }

    /** IL MOTORE SEGRETO Y: Non ripetiamo il codice per show() e hide(). Entrambi usano questo motore interno. */
    private fun animate(
        view: View,
        targetTranslationY: Float,
        hiddenOffset: Float,
        motionInterpolator: TimeInterpolator,
        onEnd: (() -> Unit)?,
    ) {
        val safeHidden = hiddenOffset.coerceAtLeast(1f) // Previene divisioni per zero
        val start = view.translationY

        // Se il menu è già dove volevamo, non far partire inutilmente il metronomo
        if (start == targetTranslationY) {
            view.alpha = 1f
            onEnd?.invoke()
            return
        }

        cancel()

        // Se il menu è già a metà strada, accorcia il tempo per non farlo rallentare troppo
        val distance = abs(targetTranslationY - start)
        val duration = (KEYBOARD_DURATION_MS * (distance / safeHidden))
            .toLong()
            .coerceIn(MIN_INTERRUPTED_DURATION_MS, KEYBOARD_DURATION_MS)

        // 1. Accendi il metronomo e passagli lo spartito (motionInterpolator)
        animator = ValueAnimator.ofFloat(start, targetTranslationY).apply {
            this.duration = duration
            interpolator = motionInterpolator

            // 2. Ad ogni tick del metronomo, l'operaio sposta i pixel
            addUpdateListener { valueAnimator ->
                view.translationY = valueAnimator.animatedValue as Float
            }

            // 3. Pulisci tutto quando l'animazione finisce o viene interrotta bruscamente
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (animator === animation) animator = null
                    if (!cancelled) onEnd?.invoke()
                }
            })

            start()
        }
    }

    /** IL MOTORE SEGRETO ALTEZZA: Cambia fisicamente le dimensioni della View invece della sua posizione Y. */
    private fun animateHeight(
        view: View,
        targetHeight: Int,
        fullHeight: Int,
        motionInterpolator: TimeInterpolator,
        onEnd: (() -> Unit)?,
    ) {
        val params = view.layoutParams ?: return
        val startHeight = when {
            params.height >= 0 -> params.height
            view.height > 0 -> view.height
            else -> 0
        }

        if (startHeight == targetHeight) {
            params.height = targetHeight
            view.layoutParams = params
            onEnd?.invoke()
            return
        }

        cancel()

        val distance = abs(targetHeight - startHeight)
        val safeFullHeight = fullHeight.coerceAtLeast(1)
        val duration = (KEYBOARD_DURATION_MS * (distance.toFloat() / safeFullHeight))
            .toLong()
            .coerceIn(MIN_INTERRUPTED_DURATION_MS, KEYBOARD_DURATION_MS)

        animator = ValueAnimator.ofInt(startHeight, targetHeight).apply {
            this.duration = duration
            interpolator = motionInterpolator

            addUpdateListener { valueAnimator ->
                val height = valueAnimator.animatedValue as Int
                val currentParams: ViewGroup.LayoutParams = view.layoutParams
                if (currentParams.height != height) {
                    currentParams.height = height
                    view.layoutParams = currentParams
                }
            }

            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (animator === animation) animator = null
                    if (!cancelled) onEnd?.invoke()
                }
            })

            start()
        }
    }

    private companion object {
        const val KEYBOARD_DURATION_MS = 250L
        const val MIN_INTERRUPTED_DURATION_MS = 80L
    }
}
