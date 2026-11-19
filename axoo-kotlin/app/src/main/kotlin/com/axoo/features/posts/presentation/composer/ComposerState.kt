package com.axoo.features.posts.presentation.composer

enum class ComposerPanelState {
    Hidden,
    Keyboard,
    Emoji,
    Transitioning,
}

data class ComposerState(
    val panel: ComposerPanelState = ComposerPanelState.Hidden,
)
