package com.axoo.features.posts.presentation.media.components

import com.axoo.features.posts.domain.RemoteMedia
import kotlin.math.roundToInt

internal data class JustifiedMediaRow(
    val items: List<RemoteMedia>,
    val widthsPx: List<Int>,
    val heightPx: Int,
)

internal fun buildJustifiedMediaRows(
    items: List<RemoteMedia>,
    contentWidthPx: Int,
    spacingPx: Int,
    targetRowHeightPx: Int,
    maximumItemsPerRow: Int,
): List<JustifiedMediaRow> {
    if (items.isEmpty() || contentWidthPx <= 0) return emptyList()

    val rows = mutableListOf<JustifiedMediaRow>()
    var cursor = 0
    while (cursor < items.size) {
        val rowItems = mutableListOf<RemoteMedia>()
        val ratios = mutableListOf<Float>()
        var expectedWidth = 0f

        while (cursor < items.size && rowItems.size < maximumItemsPerRow) {
            val item = items[cursor++]
            rowItems += item
            ratios += item.layoutAspectRatio()
            expectedWidth = ratios.sum() * targetRowHeightPx +
                spacingPx * (ratios.size - 1)
            if (expectedWidth >= contentWidthPx) break
        }

        val isLastRow = cursor == items.size
        val availableWidth = (
            contentWidthPx - spacingPx * (rowItems.size - 1)
            ).coerceAtLeast(1)
        val naturalHeight = (availableWidth / ratios.sum().coerceAtLeast(0.1f)).roundToInt()
        val height = if (isLastRow && expectedWidth < contentWidthPx) {
            targetRowHeightPx
        } else {
            naturalHeight
        }.coerceAtLeast(1)

        val naturalWidths = ratios.map { (it * height).roundToInt().coerceAtLeast(1) }
        val shouldFillRow = !isLastRow ||
            expectedWidth >= contentWidthPx ||
            naturalWidths.sum() > availableWidth
        val widths = if (shouldFillRow) {
            naturalWidths.fitExactly(availableWidth)
        } else {
            naturalWidths
        }
        rows += JustifiedMediaRow(rowItems, widths, height)
    }
    return rows
}

private fun RemoteMedia.layoutAspectRatio(): Float =
    if (width > 0 && height > 0) {
        (width.toFloat() / height).coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
    } else {
        DEFAULT_ASPECT_RATIO
    }

private fun List<Int>.fitExactly(targetWidth: Int): List<Int> {
    if (isEmpty()) return this
    val sourceWidth = sum().coerceAtLeast(1)
    var usedWidth = 0
    return mapIndexed { index, width ->
        if (index == lastIndex) {
            (targetWidth - usedWidth).coerceAtLeast(1)
        } else {
            val fitted = (
                width * targetWidth.toFloat() / sourceWidth
                ).roundToInt().coerceAtLeast(1)
            usedWidth += fitted
            fitted
        }
    }
}

private const val DEFAULT_ASPECT_RATIO = 1f
private const val MIN_ASPECT_RATIO = 0.55f
private const val MAX_ASPECT_RATIO = 2.4f
