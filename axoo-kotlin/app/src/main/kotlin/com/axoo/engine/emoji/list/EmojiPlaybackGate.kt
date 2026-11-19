package com.axoo.engine.emoji.list

// Definisce se le emoji possono muoversi o se devono congelarsi per risparmiare batteria
enum class EmojiPlaybackGate(
    val playbackAllowed: Boolean,
) {
    Inactive(playbackAllowed = false),
    FullyVisible(playbackAllowed = true),
    InteractiveTransition(playbackAllowed = true),
    ActiveFrozen(playbackAllowed = false),
}

// Calcola il verdetto finale incrociando lo stato dello schermo con la presenza fisica della View
internal fun computeEmojiPlaybackAllowed(
    gate: EmojiPlaybackGate,
    isAttachedToWindow: Boolean,
): Boolean {
    return isAttachedToWindow && gate.playbackAllowed
}

// Congela le emoji solo per le sotto-schermate non visibili (es. pagine nascoste di un ViewPager)
internal fun EmojiPlaybackGate.withSurfaceFullyVisible(
    surfaceFullyVisible: Boolean,
): EmojiPlaybackGate {
    if (this == EmojiPlaybackGate.Inactive) return this
    return if (surfaceFullyVisible && playbackAllowed) {
        EmojiPlaybackGate.FullyVisible
    } else {
        EmojiPlaybackGate.ActiveFrozen
    }
}
