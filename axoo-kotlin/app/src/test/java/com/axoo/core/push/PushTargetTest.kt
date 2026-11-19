package com.axoo.core.push

import org.junit.Assert.*
import org.junit.Test

class PushTargetTest {
    private val data = mapOf(
        "type" to "post_comment",
        "post_id" to "11111111-1111-4111-8111-111111111111",
        "comment_id" to "22222222-2222-4222-8222-222222222222",
        "recipient_id" to "33333333-3333-4333-8333-333333333333",
    )

    @Test fun `comment and reply preserve navigation and recipient identifiers`() {
        for (kind in listOf("post_comment", "comment_reply")) {
            val target = PushTarget.from(data + ("type" to kind))!!
            assertEquals(data["post_id"], target.postId)
            assertEquals(data["comment_id"], target.commentId)
            assertEquals(data["recipient_id"], target.recipientId)
        }
    }

    @Test fun `malformed or unknown payload cannot open a screen`() {
        assertNull(PushTarget.from(emptyMap()))
        assertNull(PushTarget.from(data + ("type" to "unknown")))
        for (key in listOf("post_id", "comment_id", "recipient_id")) {
            assertNull(PushTarget.from(data - key))
            assertNull(PushTarget.from(data + (key to "invalid")))
        }
    }
}
