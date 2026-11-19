package com.axoo.features.posts.presentation.media.components

import com.axoo.features.posts.domain.RemoteMedia
import com.axoo.features.posts.domain.RemoteMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JustifiedMediaLayoutTest {
    @Test
    fun completedRowsFillTheAvailableWidthAndKeepDifferentItemWidths() {
        val rows = buildJustifiedMediaRows(
            items = listOf(
                media("wide", 400, 200),
                media("portrait", 100, 200),
                media("square", 200, 200),
                media("last", 200, 200),
            ),
            contentWidthPx = 500,
            spacingPx = 4,
            targetRowHeightPx = 150,
            maximumItemsPerRow = 4,
        )

        assertEquals(2, rows.size)
        assertEquals(500, rows.first().widthsPx.sum() + 4 * (rows.first().items.size - 1))
        assertTrue(rows.first().widthsPx.first() > rows.first().widthsPx[1])
        assertEquals(150, rows.last().heightPx)
    }

    @Test
    fun lastIncompleteRowKeepsNaturalWidths() {
        val row = buildJustifiedMediaRows(
            items = listOf(media("wide", 400, 200), media("portrait", 100, 200)),
            contentWidthPx = 500,
            spacingPx = 4,
            targetRowHeightPx = 120,
            maximumItemsPerRow = 4,
        ).single()

        assertEquals(120, row.heightPx)
        assertEquals(listOf(240, 66), row.widthsPx)
    }

    @Test
    fun completedRowsKeepTheirNaturalAspectRatioInsteadOfCreatingInnerBars() {
        val row = buildJustifiedMediaRows(
            items = listOf(media("wide-1", 480, 200), media("wide-2", 480, 200)),
            contentWidthPx = 500,
            spacingPx = 4,
            targetRowHeightPx = 150,
            maximumItemsPerRow = 4,
        ).single()

        assertEquals(103, row.heightPx)
        assertEquals(500, row.widthsPx.sum() + 4)
        row.widthsPx.forEach { width ->
            assertEquals(2.4f, width.toFloat() / row.heightPx, 0.02f)
        }
    }

    private fun media(id: String, width: Int, height: Int) = RemoteMedia(
        id = id,
        previewUrl = "https://example.com/$id.webp",
        mediaUrl = "https://example.com/$id.gif",
        width = width,
        height = height,
        title = id,
        type = RemoteMediaType.Gif,
    )
}
