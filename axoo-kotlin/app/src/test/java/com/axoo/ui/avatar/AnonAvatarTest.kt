package com.axoo.ui.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AnonAvatarTest {

    @Test
    fun `generated avatars use the curated Adventurer palette`() {
        val options = adventurerAvatarOptions()

        assertEquals(41, options.hair.size)
        listOf("long06", "long17", "long18", "long22").forEach {
            assertFalse(it in options.hair)
        }
        assertEquals(listOf("f2d3b1", "ecad80", "9e5622", "763900"), options.skinColor)
        assertEquals(
            listOf(
                "ff8fab",
                "ffb703",
                "4cc9a7",
                "4d96ff",
                "b57bff",
                "ff6b35",
                "00b4d8",
                "8ac926",
                "e63946",
                "6a4c93",
            ),
            options.backgroundColor,
        )
    }

    @Test
    fun `generated avatars omit unwanted eyes and mouths`() {
        val options = adventurerAvatarOptions()

        assertEquals(21, options.eyes.size)
        listOf("variant08", "variant09", "variant10", "variant15", "variant16").forEach {
            assertFalse(it in options.eyes)
        }

        assertEquals(17, options.mouth.size)
        listOf(
            "variant03",
            "variant04",
            "variant05",
            "variant06",
            "variant07",
            "variant08",
            "variant09",
            "variant10",
            "variant11",
            "variant13",
            "variant14",
            "variant18",
            "variant20",
        ).forEach {
            assertFalse(it in options.mouth)
        }

        assertEquals(14, options.eyebrows.size)
        assertFalse("variant04" in options.eyebrows)
    }

    @Test
    fun `empty server config receives all curated defaults`() {
        val config = avatarConfigForEditing("same-user", emptyMap())
        val options = adventurerAvatarOptions()

        assertEquals("f2d3b1", config["skinColor"])
        assertEquals(true, config["backgroundColor"] in options.backgroundColor)
        assertEquals(true, config["eyesVariant"] in options.eyes)
        assertEquals(true, config["mouthVariant"] in options.mouth)
    }

    @Test
    fun `editor can preserve every allowed skin tone`() {
        val config = avatarConfigForEditing(
            seed = "same-user",
            config = mapOf("skinColor" to "763900"),
        )

        assertEquals("763900", config["skinColor"])
    }

    @Test
    fun `saved config cannot restore excluded avatar values`() {
        val config = avatarConfigForEditing(
            seed = "same-user",
            config = mapOf(
                "hairVariant" to "long26",
                "eyesVariant" to "variant09",
                "mouthVariant" to "variant04",
                "skinColor" to "not-a-color",
                "backgroundColor" to "b6e3f4",
            ),
        )
        val options = adventurerAvatarOptions()

        assertEquals("long26", config["hairVariant"])
        assertEquals(true, config["eyesVariant"] in options.eyes)
        assertEquals(true, config["mouthVariant"] in options.mouth)
        assertEquals("f2d3b1", config["skinColor"])
        assertEquals(true, config["backgroundColor"] in options.backgroundColor)
    }
}
