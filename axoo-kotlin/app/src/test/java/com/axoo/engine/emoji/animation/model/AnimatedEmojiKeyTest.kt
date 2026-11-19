package com.axoo.engine.emoji.animation.model

import com.axoo.engine.emoji.animation.drawable.*
import com.axoo.engine.emoji.animation.rendering.*
import com.axoo.engine.emoji.animation.time.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AnimatedEmojiKeyTest {

    @Test
    fun equal_identity_asset_and_size_produce_equal_keys() {
        val first = AnimatedEmojiKey(
            id = "fire",
            path = "emoji/fire.tgs",
            sizePx = 96,
        )
        val second = AnimatedEmojiKey(
            id = "fire",
            path = "emoji/fire.tgs",
            sizePx = 96,
        )

        assertEquals(first, second)
    }

    @Test
    fun playback_key_ignores_raster_size() {
        val small = AnimatedEmojiKey(
            id = "fire",
            path = "emoji/fire.tgs",
            sizePx = 48,
        )
        val large = AnimatedEmojiKey(
            id = "fire",
            path = "emoji/fire.tgs",
            sizePx = 96,
        )

        assertEquals(small.playbackKey, large.playbackKey)
        assertNotEquals(small, large)
    }

    @Test
    fun different_files_do_not_share_a_cache_entry() {
        val key = AnimatedEmojiKey("fire", "emoji/fire.tgs", 96)
        assertNotEquals(key, key.copy(path = "emoji/other.tgs"))
        assertNotEquals(key.playbackKey, key.copy(path = "emoji/other.tgs").playbackKey)
    }

    @Test
    fun accepts_tgs_and_json_assets() {
        assertEquals("emoji/fire.tgs", AnimatedEmojiKey("fire", "emoji/fire.tgs", 96).path)
        assertEquals("emoji/fire.json", AnimatedEmojiKey("fire", "emoji/fire.json", 96).path)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_blank_path() {
        AnimatedEmojiKey("fire", " ", 96)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_unsupported_format() {
        AnimatedEmojiKey("fire", "emoji/fire.webp", 96)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_nonpositive_size() {
        AnimatedEmojiKey("fire", "emoji/fire.tgs", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_blank_identity() {
        AnimatedEmojiKey(
            id = " ",
            path = "emoji/fire.tgs",
            sizePx = 96,
        )
    }
}
