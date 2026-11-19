package com.axoo.engine.lottie

import android.graphics.Rect

// Contratto per i componenti UI (es. View, Drawable) che ospitano un'animazione.
interface LottieHost {

    // Indica se il componente è collegato e disponibile per il rendering.
    val isAttachedForLottie: Boolean

    // Richiede al componente di ridisegnare l'area (Rect) del nuovo frame.
    fun invalidateLottie(bounds: Rect? = null)
}
