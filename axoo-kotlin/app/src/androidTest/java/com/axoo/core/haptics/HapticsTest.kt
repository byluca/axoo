package com.axoo.core.haptics

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HapticsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun commentSendUsesOneConfirmationFeedback() {
        instrumentation.runOnMainSync {
            val view = RecordingView(instrumentation.targetContext)
            Haptics.commentSent(view)
            assertEquals(listOf(HapticFeedbackConstants.KEYBOARD_TAP), view.feedback)
        }
    }

    @Test
    fun backClickUsesOneFeedback() {
        instrumentation.runOnMainSync {
            val view = RecordingView(instrumentation.targetContext)
            Haptics.click(view)
            assertEquals(1, view.feedback.size)
        }
    }

    private class RecordingView(context: Context) : View(context) {
        val feedback = mutableListOf<Int>()
        override fun performHapticFeedback(feedbackConstant: Int, flags: Int): Boolean {
            feedback += feedbackConstant
            return true
        }
    }
}
