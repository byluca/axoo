package com.axoo.engine.lottie

import com.axoo.engine.emoji.animation.drawable.*
import com.axoo.engine.emoji.animation.model.*
import com.axoo.engine.emoji.animation.rendering.*
import com.axoo.engine.emoji.animation.time.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RLottieAnimationTest {

    @Test
    fun close_destroys_native_pointer_only_once() {
        val destroyedPointers = mutableListOf<Long>()
        val animation = animation(destroyNative = destroyedPointers::add)

        animation.close()
        animation.close()

        assertTrue(animation.isClosed)
        assertEquals(listOf(42L), destroyedPointers)
    }

    @Test
    fun native_pointer_is_available_until_close() {
        val animation = animation()

        assertFalse(animation.isClosed)
        assertEquals(42L, animation.withNativePointer { it })

        animation.close()

        assertNull(animation.withNativePointer { it })
    }

    private fun animation(
        destroyNative: (Long) -> Unit = {},
    ) = RLottieAnimation(
        assetPath = "emoji/fire.tgs",
        totalFrames = 60,
        frameRate = 30f,
        width = 512,
        height = 512,
        nativePointer = 42L,
        destroyNative = destroyNative,
    )
}
