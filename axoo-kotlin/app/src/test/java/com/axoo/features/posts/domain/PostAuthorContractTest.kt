package com.axoo.features.posts.domain

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Test

class PostAuthorContractTest {
    private val adapter = Moshi.Builder()
        .build()
        .adapter(Post::class.java)

    @Test
    fun serverNicknameIsPreserved() {
        val post = adapter.fromJson(
            """
            {
              "id":"post-1",
              "user_id":"user-1",
              "nickname":"VolpeCuriosa",
              "university_id":"university-1",
              "created_at":"2026-07-04T12:00:00Z"
            }
            """.trimIndent(),
        )

        assertEquals("VolpeCuriosa", post?.nickname)
    }

    @Test
    fun rollingBackendWithoutNicknameDoesNotBreakTheWholeFeed() {
        val post = adapter.fromJson(
            """
            {
              "id":"post-1",
              "user_id":"user-1",
              "university_id":"university-1",
              "created_at":"2026-07-04T12:00:00Z"
            }
            """.trimIndent(),
        )

        assertEquals("", post?.nickname)
    }
}
