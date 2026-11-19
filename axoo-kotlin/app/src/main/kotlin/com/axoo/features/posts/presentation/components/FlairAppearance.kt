package com.axoo.features.posts.presentation.components

import androidx.annotation.ColorInt

internal data class FlairAppearance(
    @ColorInt val background: Int,
    @ColorInt val foreground: Int,
    val iconName: String? = null,
)

internal fun flairDisplayLabel(flair: String): String = flair.trim().uppercase()

internal fun flairAppearance(flair: String): FlairAppearance = when (flair.trim().lowercase()) {
    "meme" -> FlairAppearance(background = 0xFFD8BCFF.toInt(), foreground = 0xFF6A2EAA.toInt(), iconName = "smiling-filled")
    "spotted" -> FlairAppearance(background = 0xFFFFACD3.toInt(), foreground = 0xFF901C5B.toInt(), iconName = "heart-filled")
    "esami" -> FlairAppearance(background = 0xFFA7D5FA.toInt(), foreground = 0xFF185B92.toInt(), iconName = "book-filled")
    // removed burocrazia
    // removed coinquilini
    "confessioni" -> FlairAppearance(background = 0xFF9EE0C8.toInt(), foreground = 0xFF196149.toInt(), iconName = "messages")
    "party" -> FlairAppearance(background = 0xFFF5D57A.toInt(), foreground = 0xFF70500D.toInt(), iconName = "party-filled")
    // Preserve the appearance of posts published with the previous categories.
    "dell'andro", "fraccacreta" -> FlairAppearance(
        background = 0xFFF5B292.toInt(),
        foreground = 0xFF7C321F.toInt(),
        iconName = "home2-filled",
    )
    else -> FlairAppearance(background = 0xFFF0ECF2.toInt(), foreground = 0xFF514A55.toInt())
}
