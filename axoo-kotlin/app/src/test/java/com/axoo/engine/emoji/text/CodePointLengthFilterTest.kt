package com.axoo.engine.emoji.text

import android.text.Spanned
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class CodePointLengthFilterTest {
    private fun dest(text: String): Spanned = mockk<Spanned>().also { value ->
        every { value.length } returns text.length
        every { value[any()] } answers { text[firstArg()] }
    }

    @Test fun `80 emoji fit without counting surrogate pairs twice`() {
        val emoji = "😀".repeat(80)
        assertNull(CodePointLengthFilter(80).filter(emoji,0,emoji.length,dest(""),0,0))
        val tooLong = emoji + "😀"
        assertEquals(emoji, CodePointLengthFilter(80).filter(tooLong,0,tooLong.length,dest(""),0,0))
    }

    @Test fun `replacement returns capacity occupied by selected text`() {
        assertEquals("😀", CodePointLengthFilter(2).filter("😀a",0,3,dest("ab"),0,1))
    }
}
