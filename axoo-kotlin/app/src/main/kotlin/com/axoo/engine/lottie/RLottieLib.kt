package com.axoo.engine.lottie

import android.graphics.Bitmap

// Wrapper JNI per il motore C++ rlottie.
object RLottieLib {

    // Codici di stato JNI.
    const val RENDER_SUCCESS = 0
    const val RENDER_BUSY = -5

    init {
        System.loadLibrary("rlottie_jni")
    }

    // Inizializza un'animazione dal JSON e restituisce il puntatore C++.
    @JvmStatic
    external fun createFromData(json: String, key: String): Long

    // Dealloca l'animazione nativa associata al puntatore.
    @JvmStatic
    external fun destroy(ptr: Long)

    // Recupera i metadati dell'animazione: [totalFrames, frameRate, width, height].
    @JvmStatic
    external fun getInfo(ptr: Long): FloatArray?

    // Renderizza il frame specifico scrivendo i pixel direttamente nella Bitmap fornita.
    @JvmStatic
    fun renderFrame(ptr: Long, frameNum: Int, bitmap: Bitmap): Int =
        nativeRenderFrame(ptr, frameNum, bitmap)

    @JvmStatic
    private external fun nativeRenderFrame(ptr: Long, frameNum: Int, bitmap: Bitmap): Int
}
