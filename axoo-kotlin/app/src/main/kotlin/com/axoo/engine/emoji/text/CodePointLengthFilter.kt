package com.axoo.engine.emoji.text

import android.text.InputFilter
import android.text.Spanned

/** Matches the server's rune limit without splitting surrogate pairs. */
internal class CodePointLengthFilter(private val max: Int) : InputFilter {
    override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
        val remaining = max - Character.codePointCount(dest, 0, dest.length) + Character.codePointCount(dest, dstart, dend)
        if (remaining <= 0) return ""
        if (Character.codePointCount(source, start, end) <= remaining) return null
        val cut = Character.offsetByCodePoints(source, start, remaining)
        return source.subSequence(start, cut)
    }
}
