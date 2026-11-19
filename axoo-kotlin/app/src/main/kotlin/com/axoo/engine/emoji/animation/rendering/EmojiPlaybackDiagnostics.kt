package com.axoo.engine.emoji.animation.rendering

import android.util.Log
import com.axoo.BuildConfig

/** Solo nelle build di sviluppo: nessun testo dei post e nessun log per frame. */
internal object EmojiPlaybackDiagnostics {
    val enabled = BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "profile"

    fun log(message: String) {
        if (enabled) Log.d("AxooEmoji", message)
    }
}

/** Campionato indipendentemente dal frame clock, per rilevare anche tick mancanti. */
internal class EmojiPlaybackStallTracker {
    enum class Event { STALLED, RECOVERED }

    private var lastProgress: Long? = null
    private var lastProgressAt = 0L
    private var stalled = false

    fun sample(active: Boolean, progress: Long, nowMs: Long): Event? {
        if (!active) {
            lastProgress = null
            stalled = false
            return null
        }
        if (lastProgress != progress) {
            val recovered = stalled
            lastProgress = progress
            lastProgressAt = nowMs
            stalled = false
            return if (recovered) Event.RECOVERED else null
        }
        if (!stalled && nowMs - lastProgressAt >= 6_000L) {
            stalled = true
            return Event.STALLED
        }
        return null
    }
}
