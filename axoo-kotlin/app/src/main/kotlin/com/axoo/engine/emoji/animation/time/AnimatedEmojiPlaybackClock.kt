package com.axoo.engine.emoji.animation.time

/**
 * Sincronizza le copie della stessa emoji usando un tempo iniziale condiviso per chiave.
 */
internal object AnimatedEmojiPlaybackClock {

    private const val MAX_SESSIONS = 512

    private val sessions = LinkedHashMap<String, Long>(16, 0.75f, true)

    fun frameAt(
        key: String,
        timeline: PlaybackTimeline,
        frameTimeNanos: Long,
    ): Int = synchronized(sessions) {

        val startedAtNanos = sessions[key] ?: frameTimeNanos.also {
            sessions[key] = it
            trimLocked()
        }

        timeline.frameAt(
            startFrame = 0,
            elapsedNanos = frameTimeNanos - startedAtNanos,
        )
    }

    private fun trimLocked() {
        while (sessions.size > MAX_SESSIONS) {
            val iterator = sessions.entries.iterator()
            iterator.next()
            iterator.remove()
        }
    }

    fun clearForTests() {
        synchronized(sessions) {
            sessions.clear()
        }
    }
}
