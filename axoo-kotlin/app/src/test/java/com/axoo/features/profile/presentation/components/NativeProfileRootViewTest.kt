package com.axoo.features.profile.presentation.components

import com.axoo.features.profile.domain.User
import com.axoo.features.profile.presentation.ProfileUiState
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProfileRootViewTest {

    @Test
    fun changingUser_rebuildsProfileContent() {
        val previous = ProfileUiState(user = user())
        val next = previous.copy(user = user().copy(nickname = "Nuovo nome"))

        assertTrue(shouldRenderProfileContent(previous, next))
    }

    @Test
    fun firstRender_buildsProfileContent() {
        assertTrue(shouldRenderProfileContent(null, ProfileUiState(user = user())))
    }

    private fun user() = User(
        id = "user-id",
        nickname = "Utente",
        universityId = "university-id",
        createdAt = "2026-08-10T10:00:00Z",
    )
}

