package com.axoo.engine.emoji.animation.model

/**
 * Descrive cosa caricare e a quale dimensione renderizzarlo, senza possedere risorse native.
 * Il registry condivide il drawable tra richieste con tutti i campi uguali.
 */
data class AnimatedEmojiKey(
    val id: String,
    val path: String,
    val sizePx: Int,
) {
    init {
        require(id.isNotBlank()) { "Animated emoji id must not be blank" }
        require(sizePx > 0) { "Animated emoji size must be positive" }
        require(path.isNotBlank()) { "Lottie asset path must not be blank" }
        require(path.endsWith(".tgs", ignoreCase = true) || path.endsWith(".json", ignoreCase = true)) {
            "Lottie asset must be a .tgs or .json file: $path"
        }
    }

    val playbackKey: String
        get() = "$id:$path"
}
