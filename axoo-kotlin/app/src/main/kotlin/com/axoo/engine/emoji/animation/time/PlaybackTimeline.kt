package com.axoo.engine.emoji.animation.time

import kotlin.math.floor

/**
 * Calcola il frame in loop dal tempo trascorso, indipendentemente dalla frequenza dei tick.
 */
class PlaybackTimeline(
    val totalFrames: Int,
    val frameRate: Float,
    private val maximumFrameRate: Float = Float.POSITIVE_INFINITY,
) {
    init {
        require(totalFrames > 0)
        require(frameRate > 0f)
        require(maximumFrameRate > 0f)
    }

    fun frameAt(
        startFrame: Int,
        elapsedNanos: Long,
    ): Int {
        val elapsedFrames = if (elapsedNanos <= 0L) {
            0L
        } else {
            val elapsedSeconds = elapsedNanos.toDouble() / NANOS_PER_SECOND
            // Skip source frames while retaining the original animation duration.
            val sampledSeconds = if (maximumFrameRate < frameRate) {
                floor(elapsedSeconds * maximumFrameRate) / maximumFrameRate
            } else {
                elapsedSeconds
            }
            (sampledSeconds * frameRate).toLong()
        }

        return ((startFrame.toLong() + elapsedFrames) % totalFrames).toInt()
    }

    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
