package com.axoo.features.posts.domain

import com.axoo.features.posts.data.PostsApi
import com.axoo.features.posts.data.dto.CreateCommentRequest
import com.axoo.features.posts.data.dto.CreatePostRequest
import com.axoo.features.posts.data.dto.UpdateCommentRequest
import com.axoo.features.posts.data.dto.VoteRequest
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.PUT

class BackendContractTest {
    private val moshi = Moshi.Builder().build()

    @Test
    fun commentCreationUsesStableClientIdAndOptionalReplyTarget() {
        val json = moshi.adapter(CreateCommentRequest::class.java).toJson(
            CreateCommentRequest(
                clientId = "550e8400-e29b-41d4-a716-446655440000",
                body = "ciao",
                replyToCommentId = "comment-1",
            ),
        )

        assertTrue(json.contains("\"client_id\""))
        assertTrue(json.contains("\"reply_to_comment_id\":\"comment-1\""))
        assertFalse(json.contains("parent_id"))
    }

    @Test
    fun commentUpdateContainsOnlyEditableContent() {
        val json = moshi.adapter(UpdateCommentRequest::class.java).toJson(
            UpdateCommentRequest(body = "testo modificato"),
        )

        assertTrue(json.contains("\"body\":\"testo modificato\""))
        assertFalse(json.contains("client_id"))
    }

    @Test
    fun voteUsesDesiredIntegerState() {
        val json = moshi.adapter(VoteRequest::class.java).toJson(VoteRequest(-1))
        assertTrue(json.contains("\"value\":-1"))
        assertFalse(json.contains("type"))
    }

    @Test
    fun postAndCommentVotesUseIdempotentPutEndpoints() {
        val postVote = PostsApi::class.java.methods.single { it.name == "votePost" }
        val commentVote = PostsApi::class.java.methods.single { it.name == "voteComment" }

        assertEquals("posts/{id}/vote", postVote.getAnnotation(PUT::class.java)?.value)
        assertEquals(
            "posts/{postId}/comments/{commentId}/vote",
            commentVote.getAnnotation(PUT::class.java)?.value,
        )
    }


    @Test
    fun postMediaKeepsTypeDimensionsAndFallbackInTheApiPayload() {
        val json = moshi.adapter(CreatePostRequest::class.java).toJson(
            CreatePostRequest(
                body = "ciao",
                clientId = "550e8400-e29b-41d4-a716-446655440000",
                media = ImageMeta(
                        url = "https://media.example/original.gif",
                        thumbnailUrl = "https://media.example/preview.webp",
                        width = 480,
                        height = 640,
                        type = "gif",
                        mimeType = "image/gif",
                        previewFallbackUrl = "https://media.example/preview.gif",
                        provider = "giphy",
                        providerId = "gif-1",
                ),
            ),
        )

        assertTrue(json.contains("\"type\":\"gif\""))
        assertTrue(json.contains("\"width\":480"))
        assertTrue(json.contains("\"height\":640"))
        assertTrue(json.contains("\"preview_fallback_url\""))
        assertTrue(json.contains("\"provider_id\":\"gif-1\""))
        assertTrue(json.contains("\"client_id\""))
        assertTrue(json.contains("\"media\":{"))
        assertFalse(json.contains("\"images\""))
        assertFalse(json.contains("university_id"))
    }
}
