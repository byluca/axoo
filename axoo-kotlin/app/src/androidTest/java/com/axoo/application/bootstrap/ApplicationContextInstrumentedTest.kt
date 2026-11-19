package com.axoo.application.bootstrap

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationContextInstrumentedTest {
    @Test
    fun target_context_uses_application_id() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.axoo", appContext.packageName)
    }
}
