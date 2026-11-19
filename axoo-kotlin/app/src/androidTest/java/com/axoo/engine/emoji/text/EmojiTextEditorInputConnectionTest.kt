package com.axoo.engine.emoji.text

import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.engine.emoji.catalog.Emoji
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmojiTextEditorInputConnectionTest {

    @Test
    fun autocorrect_replaces_the_complete_plain_text_word() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val result = AtomicReference<String>()

        instrumentation.runOnMainSync {
            val editor = createEditor(initialText = "ciao a rutti")
            val view = editor.getView() as EditText
            view.setSelection(view.length())
            val connection = view.onCreateInputConnection(EditorInfo())
            assertNotNull(connection)

            connection!!.deleteSurroundingText(5, 0)
            connection.commitText("tutti", 1)
            result.set(view.text.toString())
            editor.dispose()
        }

        assertEquals("ciao a tutti", result.get())
    }

    @Test
    fun backspace_still_deletes_a_custom_emoji_as_one_unit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val result = AtomicReference<String>()

        instrumentation.runOnMainSync {
            val editor = createEditor(
                emojis = listOf(Emoji(id = 1, name = "test/emoji", fallback = "😀")),
            )
            editor.insertEmoji(1)
            val view = editor.getView() as EditText
            val connection = view.onCreateInputConnection(EditorInfo())
            assertNotNull(connection)

            connection!!.deleteSurroundingTextInCodePoints(1, 0)
            result.set(view.text.toString())
            editor.dispose()
        }

        assertEquals("", result.get())
    }

    private fun createEditor(
        initialText: String = "",
        emojis: List<Emoji> = emptyList(),
    ): EmojiTextEditor = EmojiTextEditor(
        context = InstrumentationRegistry.getInstrumentation().targetContext,
        emojis = emojis,
        initialText = initialText,
        onTextChanged = { _, _ -> },
    )
}
