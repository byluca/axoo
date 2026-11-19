/*
 * Descrive i dati e gli eventi della schermata Crea post.
 *
 * Qui definiamo cosa contiene una bozza, cosa mostra la schermata e quali
 * azioni può ricevere. Questo file non costruisce l'interfaccia e non pubblica post.
 */
package com.axoo.features.posts.presentation.posteditor

import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.features.posts.domain.Post
import com.axoo.features.posts.domain.RemoteMedia
import com.axoo.features.posts.domain.PollOptionInput
import com.axoo.features.posts.domain.isPollInputValid

internal const val MAX_CREATE_POST_CHARACTERS = 500
internal const val CREATE_POST_COUNTER_THRESHOLD = 400

internal sealed interface PostEditorMode {
    data object Create : PostEditorMode

    data class Edit(
        val postId: String,
        val initialPost: Post? = null,
    ) : PostEditorMode
}

internal fun postEditorCharacterCount(text: String): Int =
    text.codePointCount(0, text.length)

internal fun isPostEditorBodyValid(text: String): Boolean =
    text.isNotBlank() && postEditorCharacterCount(text) <= MAX_CREATE_POST_CHARACTERS

data class PostEditorDraftState(
    val bodyText: String = "",
    val bodyEntities: List<MessageEntity> = emptyList(),
    val selectedFlair: String? = null,
    val selectedMedia: SelectedPostMedia? = null,
    val pollOptions: List<PollOptionInput>? = null,
) {
    val hasContent: Boolean
        get() = hasPostEditorDraftContent(
            bodyText = bodyText,
            bodyEntitiesCount = bodyEntities.size,
            hasSelectedMedia = selectedMedia != null,

            selectedFlair = selectedFlair,
        ) || pollOptions != null
}

internal fun PostEditorDraftState.cleared(): PostEditorDraftState = PostEditorDraftState()

internal fun hasPostEditorDraftContent(
    bodyText: String,
    bodyEntitiesCount: Int,
    hasSelectedMedia: Boolean,
    selectedFlair: String?,
): Boolean =
    bodyText.isNotBlank() ||
            bodyEntitiesCount > 0 ||
            hasSelectedMedia ||

            !selectedFlair.isNullOrBlank()

internal fun hasPostEditorUnsavedChanges(
    isEditing: Boolean,
    initialDraft: PostEditorDraftState?,
    currentDraft: PostEditorDraftState,
): Boolean = if (isEditing) {
    initialDraft?.let { currentDraft != it } ?: false
} else {
    currentDraft.hasContent
}

enum class PostMediaKind(val apiValue: String) {
    Gif("gif"),
    Sticker("sticker"),
    Meme("meme"),
    Photo("photo"),
}

data class SelectedPostMedia(
    val kind: PostMediaKind,
    val url: String,
    val previewUrl: String,
    val previewFallbackUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String? = null,
    val provider: String? = null,
    val providerId: String? = null,
)

data class PostEditorUiState(
    val draft: PostEditorDraftState = PostEditorDraftState(),
    val isPublishing: Boolean = false,
    val hasCurrentUser: Boolean = false,
    val pickerOpen: Boolean = false,
    val isEditing: Boolean = false,
    val hasUnsavedChanges: Boolean = draft.hasContent,
    val isEditorReady: Boolean = true,
) {
    val canPublish: Boolean
        get() = isPostEditorBodyValid(draft.bodyText) &&
            (draft.pollOptions?.let(::isPollInputValid) != false) &&
            !isPublishing &&
            (hasCurrentUser || isEditing) &&
            isEditorReady &&
            (!isEditing || hasUnsavedChanges)

    val hasGif: Boolean
        get() = draft.selectedMedia?.kind == PostMediaKind.Gif

    val hasMeme: Boolean
        get() = draft.selectedMedia?.kind == PostMediaKind.Meme

}

sealed interface PostEditorEvent {
    data object BackTapped : PostEditorEvent
    data object PublishTapped : PostEditorEvent
    data object GiphyStickerTapped : PostEditorEvent
    data object GifTapped : PostEditorEvent
    data object MemeTapped : PostEditorEvent
    data object PhotoTapped : PostEditorEvent
    data object PollTapped : PostEditorEvent
    data object PollOptionAdded : PostEditorEvent
    data class PollOptionRemoved(val index: Int) : PostEditorEvent
    data class PollOptionChanged(val index: Int, val text: String, val entities: List<MessageEntity>) : PostEditorEvent

    data object FlairTapped : PostEditorEvent
    data object EmojiToggleTapped : PostEditorEvent
    data class RemoteMediaTapped(val media: RemoteMedia) : PostEditorEvent
    data class RemoteMediaSearchChanged(val query: String) : PostEditorEvent
    data class RemoteMediaCategorySelected(val categoryId: String?) : PostEditorEvent
    data object RemoteMediaLoadMore : PostEditorEvent
    data object RemoteMediaRetry : PostEditorEvent
    data object RemoteMediaPickerDismissed : PostEditorEvent
    data class BodyChanged(val text: String, val entities: List<MessageEntity>) : PostEditorEvent
    data object MediaRemoved : PostEditorEvent


}

internal data class PostEditorTrimmedEmojiPayload(
    val text: String,
    val entities: List<MessageEntity>,
)

internal fun trimPostEditorEmojiPayload(
    text: String,
    entities: List<MessageEntity>,
): PostEditorTrimmedEmojiPayload {
    val leading = text.length - text.trimStart().length
    val trimmed = text.trim()
    val trailingStart = leading + trimmed.length
    val adjusted = entities.mapNotNull { entity ->
        val start = entity.offset
        val end = start + entity.length
        if (start < leading || end > trailingStart) return@mapNotNull null
        entity.copy(offset = start - leading)
    }
    return PostEditorTrimmedEmojiPayload(trimmed, adjusted)
}
