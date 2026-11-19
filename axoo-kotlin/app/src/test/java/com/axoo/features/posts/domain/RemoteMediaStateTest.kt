package com.axoo.features.posts.domain

import com.axoo.features.posts.presentation.media.RemoteMediaState
import com.axoo.features.posts.presentation.media.effectiveQuery
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteMediaStateTest {
    private val category = RemoteMediaCategory(
        id = "reaction",
        label = "Reazioni",
        iconAsset = "reaction.svg",
    )

    @Test
    fun effectiveQueryLetsTypedSearchOverrideCategory() {
        val state = RemoteMediaState(
            query = "gatto",
            categories = listOf(category),
            selectedCategory = category.id,
        )

        assertEquals("gatto", state.query)
        assertEquals("gatto", state.effectiveQuery())
    }

    @Test
    fun effectiveQueryIgnoresOldCategoryWhenSearchFieldIsEmpty() {
        val state = RemoteMediaState(
            categories = listOf(category),
            selectedCategory = category.id,
        )

        assertEquals("", state.effectiveQuery())
    }

    @Test
    fun emptyGifSearchUsesTrending() {
        assertEquals("", RemoteMediaState().effectiveQuery())
    }

}
