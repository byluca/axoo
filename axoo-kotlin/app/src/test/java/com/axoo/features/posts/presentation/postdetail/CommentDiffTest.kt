package com.axoo.features.posts.presentation.postdetail

import androidx.recyclerview.widget.ListUpdateCallback
import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.features.posts.domain.Comment
import com.axoo.features.posts.presentation.postdetail.components.CommentDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentDiffTest {

    @Test
    fun serverAcknowledgementUpdatesTheSameRow() {
        val clientId = "550e8400-e29b-41d4-a716-446655440000"
        val entity = MessageEntity(offset = 0, length = 2, customEmojiId = 7)
        val local = commentItem(
            id = "local_$clientId",
            clientId = clientId,
            deliveryState = CommentDeliveryState.Sending,
            localId = clientId,
            entities = listOf(entity),
        )
        val acknowledged = commentItem(
            id = "server-comment-id",
            clientId = clientId,
            deliveryState = CommentDeliveryState.Sent,
            entities = listOf(entity),
        )
        val updates = RecordingUpdates()

        CommentDiff().calculate(
            oldList = flattenComments(listOf(local)),
            newList = flattenComments(listOf(acknowledged)),
        ).dispatchUpdatesTo(updates)

        assertEquals(local.stableKey, acknowledged.stableKey)
        assertTrue(local.hasSameRenderedBody(acknowledged))
        assertEquals(listOf("changed:0:1"), updates.events)
    }

    @Test
    fun appendingACommentDoesNotRebindThePreviousComment() {
        val first = commentItem(
            id = "first",
            clientId = "550e8400-e29b-41d4-a716-446655440001",
        )
        val second = commentItem(
            id = "second",
            clientId = "550e8400-e29b-41d4-a716-446655440002",
        )
        val updates = RecordingUpdates()

        CommentDiff().calculate(
            oldList = flattenComments(listOf(first)),
            newList = flattenComments(listOf(first, second)),
        ).dispatchUpdatesTo(updates)

        assertEquals(listOf("inserted:1:1"), updates.events)
    }

    @Test
    fun rapidOutOfOrderAcknowledgementsNeverRemoveRows() {
        val clientIds = List(10) { index -> "550e8400-e29b-41d4-a716-${index.toString().padStart(12, '0')}" }
        var current = clientIds.map { clientId ->
            commentItem(
                id = "local_$clientId",
                clientId = clientId,
                deliveryState = CommentDeliveryState.Sending,
                localId = clientId,
                entities = listOf(MessageEntity(offset = 0, length = 2, customEmojiId = 7)),
            )
        }

        clientIds.indices.reversed().forEach { index ->
            val acknowledged = current[index].copy(
                comment = current[index].comment.copy(id = "server-$index"),
                deliveryState = CommentDeliveryState.Sent,
                localId = null,
            )
            val next = current.toMutableList().also { it[index] = acknowledged }
            val updates = RecordingUpdates()

            CommentDiff().calculate(
                oldList = flattenComments(current),
                newList = flattenComments(next),
            ).dispatchUpdatesTo(updates)

            assertEquals(listOf("changed:$index:1"), updates.events)
            current = next
        }
    }

    private fun commentItem(
        id: String,
        clientId: String,
        deliveryState: CommentDeliveryState = CommentDeliveryState.Sent,
        localId: String? = null,
        entities: List<MessageEntity> = emptyList(),
    ) = CommentUiModel(
        comment = Comment(
            id = id,
            postId = "post-id",
            clientId = clientId,
            userId = "user-id",
            nickname = "Tester",
            body = "🔥",
            entities = entities,
            createdAt = "2026-08-11T20:00:00Z",
        ),
        deliveryState = deliveryState,
        localId = localId,
    )

    private class RecordingUpdates : ListUpdateCallback {
        val events = mutableListOf<String>()

        override fun onInserted(position: Int, count: Int) {
            events += "inserted:$position:$count"
        }

        override fun onRemoved(position: Int, count: Int) {
            events += "removed:$position:$count"
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            events += "moved:$fromPosition:$toPosition"
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            events += "changed:$position:$count"
        }
    }
}
