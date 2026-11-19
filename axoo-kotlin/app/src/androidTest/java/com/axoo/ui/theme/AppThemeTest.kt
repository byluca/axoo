package com.axoo.ui.theme

import android.content.res.Configuration
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.R
import com.axoo.application.bootstrap.MainActivity
import com.axoo.ui.sheets.BottomSheetContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppThemeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun savedChoiceOverridesTheSystem() {
        val context = instrumentation.targetContext
        val previous = AppTheme.isDark(context)
        try {
            for (systemMode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                val system = context.createConfigurationContext(Configuration().apply { uiMode = systemMode })
                for (dark in listOf(false, true)) {
                    AppTheme.setDark(context, dark)
                    assertEquals(dark, AppTheme.isDark(system))
                    assertFalse(AppTheme.setDark(context, dark))
                    val themed = AppTheme.wrap(system)
                    val expected = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    assertEquals(expected, themed.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                }
            }
        } finally {
            AppTheme.setDark(context, previous)
        }
    }

    @Test
    fun activityRecreationAppliesTheSavedPalette() {
        val context = instrumentation.targetContext
        val previous = AppTheme.isDark(context)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                for (dark in listOf(false, true, false)) {
                    scenario.onActivity { AppTheme.setDark(it, dark) }
                    scenario.recreate()
                    scenario.onActivity { activity ->
                        val expected = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                        assertEquals(expected, activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                        assertEquals(activity.getColor(R.color.axoo_background), NativeColors.background)
                        val sheet = BottomSheetContainer(activity)
                        sheet.setSheetContent(TextView(activity).apply { text = "Tema" })
                        try {
                            sheet.show()
                            assertEquals(expected, sheet.dialog.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                        } finally {
                            sheet.dismissImmediately()
                        }
                    }
                }
            }
        } finally {
            AppTheme.setDark(context, previous)
            instrumentation.runOnMainSync { NativeColors.initialize(AppTheme.wrap(context).resources) }
        }
    }
}
