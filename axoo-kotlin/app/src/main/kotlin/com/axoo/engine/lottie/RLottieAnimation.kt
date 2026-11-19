package com.axoo.engine.lottie

/**
 * Possiede l'animazione nativa e protegge l'accesso al puntatore con un lock.
 * close() invalida il puntatore sotto lock, poi libera la memoria nativa fuori dal lock.
 */
class RLottieAnimation internal constructor(
    val assetPath: String = "",
    val totalFrames: Int,
    val frameRate: Float,
    val width: Int,
    val height: Int,
    nativePointer: Long,
    private val destroyNative: (Long) -> Unit = RLottieLib::destroy,
) : AutoCloseable {

    private val lock = Any()
    private var pointer: Long = nativePointer

    init {
        require(nativePointer != 0L) { "Native pointer must not be zero" }
        require(totalFrames > 0) { "Animation must contain at least one frame" }
        require(frameRate > 0f) { "Animation frame rate must be positive" }
        require(width > 0 && height > 0) { "Animation dimensions must be positive" }
    }

    val isClosed: Boolean
        get() = synchronized(lock) { pointer == 0L }

    internal fun <T> withNativePointer(block: (Long) -> T): T? =
        synchronized(lock) {
            val current = pointer
            if (current == 0L) {
                null
            } else {
                block(current)
            }
        }

    override fun close() {
        val pointerToDestroy = synchronized(lock) {
            val current = pointer
            pointer = 0L
            current
        }

        if (pointerToDestroy != 0L) {
            destroyNative(pointerToDestroy)
        }
    }
}
