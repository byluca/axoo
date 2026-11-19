package com.axoo.features.posts.presentation.posteditor

import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.engine.emoji.catalog.EmojiData
import com.axoo.engine.emoji.text.EmojiTextEditor
import com.axoo.features.posts.domain.PollOptionInput
import com.axoo.features.posts.presentation.composer.ComposerController
import com.axoo.features.posts.presentation.posteditor.components.NativePostEditorBodyView
import com.axoo.ui.theme.NativeColors
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PollEmojiInputTest {
    @Test fun sharedPickerInsertsAtTheCursorInTheFocusedOptionAndThenInTheQuestion() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val changes = mutableListOf<PostEditorEvent>()
            val questionChanges = mutableListOf<String>()
            val controller = ComposerController(context)
            val body = NativePostEditorBodyView(context, NativeColors.textPrimary,
                onPayloadChanged = { text, _ -> questionChanges.add(text) }, onEmojiToggle = {}, onInputActivated = {})
            val root = FrameLayout(context).apply { addView(body) }
            body.bindComposer(controller)
            body.setOnPollEvent { changes.add(it) }
            body.bind("seed", null, "Autore", "Campus", null, "Domanda", emptyList(), null,
                false, NativeColors.textSecondary, false,
                pollOptions = listOf(PollOptionInput("ab"), PollOptionInput("cd")))
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            val pollEditor = field<Any>(body, "pollEditor")
            val rows = field<List<Any>>(pollEditor, "rows")
            val input = field<EmojiTextEditor>(rows[1], "editor")
            input.getView().requestFocus()
            input.moveCursorTo(1)
            val emoji = EmojiData.emojis.first()
            controller.onEmojiPicked(emoji)
            val optionChange = changes.filterIsInstance<PostEditorEvent.PollOptionChanged>().last()
            assertEquals(1, optionChange.index)
            assertTrue(optionChange.text.startsWith("c"))
            assertTrue(optionChange.text.endsWith("d"))
            assertEquals(1, optionChange.entities.single().offset)
            assertEquals(emoji.id, optionChange.entities.single().customEmojiId)
            assertTrue(questionChanges.isEmpty())
            val question = field<EmojiTextEditor>(body, "emojiInput")
            question.getView().requestFocus()
            question.moveCursorToEnd()
            controller.onEmojiPicked(emoji)
            assertTrue(questionChanges.last().startsWith("Domanda"))
            assertEquals(1, changes.filterIsInstance<PostEditorEvent.PollOptionChanged>().size)
            body.dispose()
        }
    }

    @Suppress("UNCHECKED_CAST") private fun <T> field(target: Any, name: String): T = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target) as T
}
