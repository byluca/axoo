package com.axoo.engine.emoji

import com.axoo.engine.emoji.catalog.EmojiData
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class EmojiDataTest {
    companion object {
        private val categoryIdPattern = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
        private val emojiKeyPattern = Regex(
            "^[a-z0-9]+(?:-[a-z0-9]+)*/[a-z0-9]+(?:-[a-z0-9]+)*$",
        )

        @JvmStatic
        @BeforeClass
        fun loadCatalog() {
            EmojiData.configureFromJson(File("src/main/assets/axooji/catalog.json").readText())
        }
    }

    @Test
    fun ids_names_and_categories_are_complete_and_unique() {
        val emojis = EmojiData.emojis
        val categoryEmojis = EmojiData.categories.flatMap { it.items }

        assertEquals(635, emojis.size)
        assertEquals(emojis.size, emojis.map { it.id }.distinct().size)
        assertEquals(emojis.size, emojis.map { it.name }.distinct().size)
        assertTrue(emojis.all { it.id > 0 })
        assertEquals(emojis.filterNot { it.pickerHidden }.map { it.name }.toSet(), categoryEmojis.map { it.name }.toSet())
        assertEquals(categoryEmojis.size, categoryEmojis.map { it.name }.distinct().size)
        assertTrue(emojis.all { it.fallback.isNotBlank() })
    }

    @Test
    fun every_emoji_and_category_icon_uses_a_catalog_animation() {
        EmojiData.emojis.forEach { emoji ->
            assertTrue("Missing ${emoji.assetPath}", File("src/main/assets/${emoji.assetPath}").isFile)
        }
        EmojiData.categories.forEach { category ->
            assertTrue(category.icon in category.items)
            assertTrue(File("src/main/assets/${category.icon.assetPath}").isFile)
        }
        assertTrue(!File("src/main/assets/axooji/previews").exists())
        assertTrue(File("src/main/assets/axooji/category-icons/recents.webp").isFile)
    }

    @Test
    fun persisted_axooji_identity_does_not_change_accidentally() {
        val payload = EmojiData.emojis
            .filter { it.id <= 1254 }
            .sortedBy { it.id }
            .joinToString("\n") { "${it.id}:${it.name}" }
            .toByteArray()
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(payload)
            .joinToString("") { "%02x".format(it) }

        assertEquals(
            "afd41036d8a32aa23dd9d5c6893a9a3a4ded06ce247739738c51d46367f8b11c",
            fingerprint,
        )
    }

    @Test
    fun catalog_uses_normalized_axooji_keys() {
        assertTrue(EmojiData.categories.all { categoryIdPattern.matches(it.id) })
        assertTrue(EmojiData.emojis.all { emojiKeyPattern.matches(it.name) })
        EmojiData.categories.forEach { category ->
            assertTrue(category.items.all { it.name.startsWith("${category.id}/") })
        }
    }

    @Test
    fun catalog_defines_picker_labels_and_order() {
        assertEquals(
            listOf(
                "via-re-david",
                "focaccia-peroni",
                "la-cicchetteria",
                "amtab",
                "mensa-adisu",
                "storie-vecchio-sud",
                "torre-quetta",
                "pane-pomodoro",
                "spiral",
                "class",
                "pack-37",
                "piccolo-bar",
                "via-orabona",
                "amici-di-axoo",
                "pack-16",
            ),
            EmojiData.categories.map { it.id },
        )
        val originalReward = EmojiData.categories.single { it.id == "amici-di-axoo" }
        assertEquals("Bari Vecchia", originalReward.label)
        val duplicates = setOf(60064, 60067, 60075, 60077, 60080, 60111)
        assertEquals((60006..60123).filterNot { it in duplicates }, originalReward.items.map { it.id })
        assertEquals(112, originalReward.items.size)
        assertTrue(duplicates.all { EmojiData.byId[it]?.pickerHidden == true })
        assertTrue(setOf(60063, 60043, 60074, 60071, 60055, 60110).all { EmojiData.byId[it]?.pickerHidden == false })
        assertTrue((60000..60005).all { EmojiData.byId[it]?.pickerHidden == true })
        assertEquals("Via Re David", EmojiData.categories[0].label)
        assertEquals("Panzerotto", EmojiData.categories[8].label)
        assertEquals("Mastro Ciccio", EmojiData.categories[9].label)
        assertEquals("Panificio Magda", EmojiData.categories[10].label)
        assertEquals(
            listOf(
                "via-re-david/sunglasses",
                "via-re-david/wink",
                "via-re-david/hearts-face",
                "via-re-david/kiss",
            ),
            EmojiData.categories[0].items.take(4).map { it.name },
        )
        assertEquals(34, EmojiData.categories[10].items.size)
    }
}
