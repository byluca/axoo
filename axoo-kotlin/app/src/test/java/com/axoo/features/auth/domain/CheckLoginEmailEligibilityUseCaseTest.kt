package com.axoo.features.auth.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckLoginEmailEligibilityUseCaseTest {

    @Test
    fun acceptsUniversityAndPersonalEmailFormats() {
        assertTrue(isValidLoginEmailFormat("studente@studenti.uniba.it"))
        assertTrue(isValidLoginEmailFormat("studente@accademiabari.it"))
        assertTrue(isValidLoginEmailFormat(" alpha.tester@gmail.com "))
    }

    @Test
    fun rejectsMalformedEmails() {
        assertFalse(isValidLoginEmailFormat("tester"))
        assertFalse(isValidLoginEmailFormat("tester@localhost"))
        assertFalse(isValidLoginEmailFormat("test er@example.com"))
        assertFalse(isValidLoginEmailFormat("a@@example.com"))
    }
}
