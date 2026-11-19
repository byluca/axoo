package com.axoo.engine.emoji.animation.rendering

import android.graphics.Bitmap
import com.axoo.engine.lottie.RLottieAnimation
import com.axoo.engine.lottie.RLottieLib

/**
 * Renderizza un frame nella bitmap fornita e traduce l'esito nativo.
 * Non conserva stato; l'accesso al puntatore è protetto da RLottieAnimation.
 */
object AnimationFrameRenderer {

    sealed interface Result {
        data object Success : Result
        data object AnimationClosed : Result
        data class NativeFailure(val code: Int) : Result
    }

    fun render(
        animation: RLottieAnimation,
        frameIndex: Int,
        target: Bitmap,
    ): Result {
        val nativeResult = animation.withNativePointer { pointer ->
            // Il bridge nativo chiama renderSync(clear = true).
            RLottieLib.renderFrame(pointer, frameIndex, target)
        } ?: return Result.AnimationClosed

        return if (nativeResult == RLottieLib.RENDER_SUCCESS) {
            Result.Success
        } else {
            Result.NativeFailure(nativeResult)
        }
    }
}
