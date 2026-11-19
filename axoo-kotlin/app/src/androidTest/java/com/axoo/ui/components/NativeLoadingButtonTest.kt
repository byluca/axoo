package com.axoo.ui.components

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeLoadingButtonTest {
    @Test
    fun loadingKeepsTheSizeAndBlocksRepeatsUntilTheActionCanBeRetried() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val control = NativeLoadingButton(context)
        val button = control.getChildAt(0) as MaterialButton
        val activity = control.getChildAt(1) as CircularProgressIndicator
        var actions = 0
        control.setOnClickListener { actions++ }
        fun measure() = control.measure(
            View.MeasureSpec.makeMeasureSpec(context.dp(400), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(context.dp(42), View.MeasureSpec.EXACTLY),
        )
        control.bind("Pubblica", enabled = true, loading = false)
        measure()
        val originalWidth = control.measuredWidth
        button.performClick()
        control.bind("Pubblica", enabled = false, loading = true)
        measure()
        assertEquals(originalWidth, control.measuredWidth)
        assertEquals(context.dp(42), control.measuredHeight)
        assertEquals(View.VISIBLE, activity.visibility)
        assertEquals(NativeColors.accent, button.backgroundTintList!!.defaultColor)
        assertFalse(button.isEnabled)
        repeat(2) { button.performClick() }
        assertEquals(1, actions)

        // The same state reset is used for a failed request and a retry.
        control.bind("Pubblica", enabled = true, loading = false)
        measure()
        assertEquals(originalWidth, control.measuredWidth)
        assertEquals(View.GONE, activity.visibility)
        button.performClick()
        assertEquals(2, actions)
    }

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
