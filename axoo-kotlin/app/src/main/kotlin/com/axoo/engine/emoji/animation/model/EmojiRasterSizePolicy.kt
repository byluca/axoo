package com.axoo.engine.emoji.animation.model

/**
 * Raggruppa le dimensioni delle bitmap per favorire cache e riuso.
 * La dimensione con cui vengono disegnate resta decisa dal layout.
 */
internal object EmojiRasterSizePolicy {
    private const val PICKER_DP = 48
    private const val INLINE_TEXT_DP = 26
    private const val INLINE_SMALL_DP = 32
    private const val INLINE_MEDIUM_DP = 48
    private const val INLINE_LARGE_DP = 64
    private const val OVERSIZE_BUCKET_PX = 8

    fun pickerSizePx(density: Float): Int = dpToPx(PICKER_DP, density)

    fun inlineRasterSizePx(renderSizePx: Int, density: Float): Int {
        val safeSize = renderSizePx.coerceAtLeast(1)
        val text = dpToPx(INLINE_TEXT_DP, density)
        val small = dpToPx(INLINE_SMALL_DP, density)
        val medium = dpToPx(INLINE_MEDIUM_DP, density)
        val large = dpToPx(INLINE_LARGE_DP, density)
        return when {
            safeSize <= text -> text
            safeSize <= small -> small
            safeSize <= medium -> medium
            safeSize <= large -> large
            else -> roundUpToOversizeBucket(safeSize)
        }
    }

    private fun dpToPx(dp: Int, density: Float): Int =
        (dp * density).toInt().coerceAtLeast(1)

    private fun roundUpToOversizeBucket(sizePx: Int): Int {
        val remainder = sizePx % OVERSIZE_BUCKET_PX
        return if (remainder == 0) sizePx else sizePx + OVERSIZE_BUCKET_PX - remainder
    }
}
