package com.axoo.features.posts.presentation.posteditor

import com.axoo.engine.emoji.text.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostEditorContractTest {
    @Test
    fun postEditorBody_acceptsUpToFiveHundredCharacters() {
        assertTrue(isPostEditorBodyValid("a".repeat(MAX_CREATE_POST_CHARACTERS)))
        assertFalse(isPostEditorBodyValid("a".repeat(MAX_CREATE_POST_CHARACTERS + 1)))
    }

    @Test
    fun characterLimit_countsUnicodeCodePointsInsteadOfUtf16Units() {
        val emojis = "😀".repeat(MAX_CREATE_POST_CHARACTERS)

        assertEquals(MAX_CREATE_POST_CHARACTERS, postEditorCharacterCount(emojis))
        assertTrue(isPostEditorBodyValid(emojis))
        assertFalse(isPostEditorBodyValid(emojis + "a"))
    }

    @Test
    fun postEditorBody_rejectsWhitespaceOnlyText() {
        assertFalse(isPostEditorBodyValid("  \n\t  "))
    }

    @Test
    fun draftAlwaysRequiresTextEvenWhenMediaIsPresent() {
        val media = SelectedPostMedia(PostMediaKind.Gif, "https://media.test/gif", "https://media.test/preview")
        for (selected in listOf(null, media)) {
            fun canPublish(text: String) = PostEditorUiState(
                draft = PostEditorDraftState(bodyText = text, selectedMedia = selected),
                hasCurrentUser = true,
            ).canPublish
            assertTrue(canPublish("testo"))
            assertFalse(canPublish(""))
            assertFalse(canPublish("  \n"))
            assertFalse(canPublish("a".repeat(MAX_CREATE_POST_CHARACTERS + 1)))
        }
    }

    @Test
    fun cleared_removesDraftContentAfterPublishing() {
        val result = PostEditorDraftState(
            bodyText = "vecchio post",
            bodyEntities = listOf(MessageEntity(offset = 0, length = 1, customEmojiId = 7)),
            selectedFlair = "spotted",
            selectedMedia = SelectedPostMedia(
                kind = PostMediaKind.Gif,
                url = "https://example.com/old.gif",
                previewUrl = "https://example.com/old-preview.gif",
            ),
        ).cleared()

        assertEquals(PostEditorDraftState(), result)
        assertFalse(result.hasContent)
    }

    @Test
    fun hasPostEditorDraftContent_returnsFalseForEmptyDraft() {
        assertFalse(
            hasPostEditorDraftContent(
                bodyText = "",
                bodyEntitiesCount = 0,
                hasSelectedMedia = false,
                selectedFlair = null,
            ),
        )
    }

    @Test
    fun hasPostEditorDraftContent_returnsTrueForNonBlankBodyText() {
        assertTrue(
            hasPostEditorDraftContent(
                bodyText = "a",
                bodyEntitiesCount = 0,
                hasSelectedMedia = false,
                selectedFlair = null,
            ),
        )
    }

    @Test
    fun hasPostEditorDraftContent_ignoresWhitespaceOnlyBodyText() {
        assertFalse(
            hasPostEditorDraftContent(
                bodyText = "  \n\t  ",
                bodyEntitiesCount = 0,
                hasSelectedMedia = false,
                selectedFlair = null,
            ),
        )
    }

    @Test
    fun hasPostEditorDraftContent_returnsTrueForBodyEntitiesOnly() {
        assertTrue(
            hasPostEditorDraftContent(
                bodyText = "",
                bodyEntitiesCount = 1,
                hasSelectedMedia = false,
                selectedFlair = null,
            ),
        )
    }

    @Test
    fun hasPostEditorDraftContent_returnsTrueForSelectedMediaOnly() {
        assertTrue(
            hasPostEditorDraftContent(
                bodyText = "",
                bodyEntitiesCount = 0,
                hasSelectedMedia = true,
                selectedFlair = null,
            ),
        )
    }

    @Test
    fun hasPostEditorDraftContent_returnsTrueForGifOnly() {
        assertTrue(
            hasPostEditorDraftContent(
                bodyText = "",
                bodyEntitiesCount = 0,
                hasSelectedMedia = true,
                selectedFlair = null,
            ),
        )
    }

    @Test
    fun hasPostEditorDraftContent_returnsTrueForSelectedFlairOnly() {
        assertTrue(
            hasPostEditorDraftContent(
                bodyText = "",
                bodyEntitiesCount = 0,
                hasSelectedMedia = false,
                selectedFlair = "spotted",
            ),
        )
    }

    @Test
    fun hasPostEditorDraftContent_returnsTrueForMultipleDraftFields() {
        assertTrue(
            hasPostEditorDraftContent(
                bodyText = "ciao",
                bodyEntitiesCount = 1,
                hasSelectedMedia = true,
                selectedFlair = "spotted",
            ),
        )
    }

    @Test
    fun editDraft_isDirtyOnlyAfterARealChange() {
        val initial = PostEditorDraftState(bodyText = "testo originale")

        assertFalse(
            hasPostEditorUnsavedChanges(
                isEditing = true,
                initialDraft = initial,
                currentDraft = initial.copy(),
            ),
        )
        assertTrue(
            hasPostEditorUnsavedChanges(
                isEditing = true,
                initialDraft = initial,
                currentDraft = initial.copy(bodyText = "testo modificato"),
            ),
        )
    }

    @Test
    fun editDraft_isNotDirtyBeforeThePostHasLoaded() {
        assertFalse(
            hasPostEditorUnsavedChanges(
                isEditing = true,
                initialDraft = null,
                currentDraft = PostEditorDraftState(),
            ),
        )
    }

    @Test
    fun editUi_requiresLoadedChangedValidDraftButNotProfile() {
        val draft = PostEditorDraftState(bodyText = "testo modificato")

        assertTrue(
            PostEditorUiState(
                draft = draft,
                isEditing = true,
                hasUnsavedChanges = true,
                isEditorReady = true,
                hasCurrentUser = false,
            ).canPublish,
        )
        assertFalse(
            PostEditorUiState(
                draft = draft,
                isEditing = true,
                hasUnsavedChanges = false,
                isEditorReady = true,
            ).canPublish,
        )
        assertFalse(
            PostEditorUiState(
                draft = draft,
                isEditing = true,
                hasUnsavedChanges = true,
                isEditorReady = false,
            ).canPublish,
        )
    }

    @Test
    fun trimPostEditorEmojiPayload_shiftsEntitiesAfterLeadingWhitespace() {
        val result = trimPostEditorEmojiPayload(
            text = "  hi 🔥  ",
            entities = listOf(MessageEntity(offset = 5, length = 2, customEmojiId = 7)),
        )

        assertEquals("hi 🔥", result.text)
        assertEquals(listOf(MessageEntity(offset = 3, length = 2, customEmojiId = 7)), result.entities)
    }

    @Test
    fun trimPostEditorEmojiPayload_dropsEntitiesIntersectingTrimmedWhitespace() {
        val result = trimPostEditorEmojiPayload(
            text = "  hi  ",
            entities = listOf(
                MessageEntity(offset = 0, length = 2, customEmojiId = 1),
                MessageEntity(offset = 3, length = 1, customEmojiId = 2),
                MessageEntity(offset = 5, length = 1, customEmojiId = 3),
            ),
        )

        assertEquals("hi", result.text)
        assertEquals(listOf(MessageEntity(offset = 1, length = 1, customEmojiId = 2)), result.entities)
    }
}
