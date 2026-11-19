package com.axoo.features.posts.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PostEditorErrorTest {
    @Test
    fun mediaValidationErrorExplainsWhatTheUserCanDo() {
        assertEquals(
            "La GIF o il meme selezionato non è più disponibile. Scegline un altro.",
            publishErrorMessage(400, "URL fallback non valido"),
        )
    }

    @Test
    fun bodyLimitFromBackendIsPreserved() {
        assertEquals(
            "Il post non può superare 500 caratteri",
            publishErrorMessage(400, "il post non può superare 500 caratteri"),
        )
    }

    @Test
    fun unknownServerDetailsAreNotExposed() {
        assertEquals(
            "Il post o il media non sono validi.",
            publishErrorMessage(400, "database detail"),
        )
    }
}
