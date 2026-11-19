package com.axoo.features.posts.presentation.components

import com.axoo.features.posts.presentation.posteditor.components.kFlairs
import org.junit.Assert.assertEquals
import org.junit.Test

class FlairAppearanceTest {
    @Test
    fun known_flair_uses_the_same_semantic_palette_everywhere() {
        assertEquals(
            FlairAppearance(
                background = 0xFFFFACD3.toInt(),
                foreground = 0xFF901C5B.toInt(),
                iconName = "heart-filled",
            ),
            flairAppearance("Spotted"),
        )
    }

    @Test
    fun unknown_flair_uses_neutral_palette() {
        assertEquals(
            FlairAppearance(
                background = 0xFFF0ECF2.toInt(),
                foreground = 0xFF514A55.toInt(),
            ),
            flairAppearance("Altro"),
        )
    }

    @Test
    fun flair_labels_are_uppercase_and_use_their_semantic_icons() {
        assertEquals("MEME", flairDisplayLabel("Meme"))
        assertEquals("smiling-filled", flairAppearance("Meme").iconName)
        assertEquals("party-filled", flairAppearance("Party").iconName)
        assertEquals("messages", flairAppearance("Confessioni").iconName)
        assertEquals("home2-filled", flairAppearance("Dell'Andro").iconName)
        assertEquals("home2-filled", flairAppearance("Fraccacreta").iconName)
        assertEquals("book-filled", flairAppearance("Esami").iconName)
    }

    @Test
    fun available_flairs_match_the_supported_order() {
        assertEquals(
            listOf(
                "Meme", "Spotted", "Esami", "Confessioni",
                "Party",
            ),
            kFlairs,
        )
    }
}
