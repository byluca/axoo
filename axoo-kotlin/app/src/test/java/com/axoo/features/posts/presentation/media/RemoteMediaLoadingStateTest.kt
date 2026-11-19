package com.axoo.features.posts.presentation.media

import com.axoo.features.posts.domain.RemoteMedia
import com.axoo.features.posts.domain.RemoteMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteMediaLoadingStateTest {
    @Test
    fun uncachedCategoryShowsLoadingSkeletonInsteadOfPreviousMemes() {
        val previousItem = RemoteMedia(
            id = "italia/1",
            previewUrl = "https://example.test/preview.webp",
            mediaUrl = "https://example.test/meme.webp",
            width = 320,
            height = 240,
            title = "Meme",
            type = RemoteMediaType.Sticker,
            category = "italia",
        )
        val loading = RemoteMediaState(
            type = RemoteMediaType.Sticker,
            items = listOf(previousItem),
            isLoadingMore = true,
            nextOffset = 30,
            error = RemoteMediaError.LoadingFailed,
            selectedCategory = "italia",
        ).startLoadingMemeCategory("world")

        assertEquals("world", loading.selectedCategory)
        assertTrue(loading.items.isEmpty())
        assertTrue(loading.isLoading)
        assertFalse(loading.isLoadingMore)
        assertNull(loading.nextOffset)
        assertNull(loading.error)
    }
}
