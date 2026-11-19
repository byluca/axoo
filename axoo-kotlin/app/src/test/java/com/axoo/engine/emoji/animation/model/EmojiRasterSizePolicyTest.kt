package com.axoo.engine.emoji.animation.model

import com.axoo.engine.emoji.animation.drawable.*
import com.axoo.engine.emoji.animation.rendering.*
import com.axoo.engine.emoji.animation.time.*
import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiRasterSizePolicyTest {

    @Test
    fun picker_size_scales_without_oversized_rasters() {
        assertEquals(144, EmojiRasterSizePolicy.pickerSizePx(3f))
        assertEquals(96, EmojiRasterSizePolicy.pickerSizePx(2f))
    }

    @Test
    fun inline_sizes_snap_to_stable_tiers() {
        assertEquals(26, EmojiRasterSizePolicy.inlineRasterSizePx(24, 1f))
        assertEquals(32, EmojiRasterSizePolicy.inlineRasterSizePx(30, 1f))
        assertEquals(48, EmojiRasterSizePolicy.inlineRasterSizePx(40, 1f))
        assertEquals(64, EmojiRasterSizePolicy.inlineRasterSizePx(56, 1f))
        assertEquals(72, EmojiRasterSizePolicy.inlineRasterSizePx(67, 1f))
    }
}
