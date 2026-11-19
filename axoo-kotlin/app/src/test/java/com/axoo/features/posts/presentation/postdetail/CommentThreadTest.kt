package com.axoo.features.posts.presentation.postdetail

import com.axoo.features.posts.domain.Comment
import org.junit.Assert.*
import org.junit.Test

class CommentThreadTest {
    private fun item(id: String, parent: String? = null, second: Int = 0) = CommentUiModel(Comment(
        id = id, postId = "post", userId = "author", nickname = "Mario", body = "Testo",
        parentCommentId = parent, createdAt = "2026-09-17T10:00:${second.toString().padStart(2, '0')}Z",
    ))

    @Test fun `roots retain server order and replies are depth first chronological`() {
        val rows = flattenComments(listOf(item("b"), item("late", "a", 9),
            item("a"), item("grandchild", "early", 5), item("early", "a", 1)))
        assertEquals(listOf("b", "a", "early", "grandchild", "late"), rows.map { it.item.id })
        assertEquals(listOf(0, 0, 1, 2, 1), rows.map { it.depth })
        assertEquals(listOf("b", "a", "a", "a", "a"), rows.map { it.rootId })
        assertEquals(listOf(true, true, false, false, false), rows.map { it.isFirst })
        assertEquals(listOf(true, false, false, false, true), rows.map { it.isLast })
    }

    @Test fun `orphan replies and cycles are never lost or duplicated`() {
        val rows = flattenComments(listOf(item("orphan", "missing"), item("a", "b"), item("b", "a")))
        assertEquals(3, rows.size)
        assertEquals(setOf("orphan", "a", "b"), rows.map { it.item.id }.toSet())
        assertEquals(0, rows.first().depth)
    }

    @Test fun `deep threads do not recurse`() {
        val items = (0..2000).map { item("$it", if (it == 0) null else "${it - 1}") }
        assertEquals(2000, flattenComments(items.reversed()).last().depth)
    }

    @Test fun `deleting a parent preserves replies and removes personal content`() {
        val items = withDeletedComment(listOf(item("a"), item("b", "a")), "a")
        assertEquals(2, items.size)
        with(items.first().comment) {
            assertTrue(isDeleted)
            assertEquals("", body)
            assertEquals("", userId)
            assertEquals("", nickname)
        }
        assertEquals(1, flattenComments(items).last().depth)
    }

    @Test fun `deleting final descendant prunes empty tombstone ancestors`() {
        val items = listOf(item("a"), item("b", "a"), item("c", "b"), item("other"))
        val deletedParents = withDeletedComment(withDeletedComment(items, "a"), "b")
        assertEquals(listOf("other"), withDeletedComment(deletedParents, "c").map { it.id })
    }

    @Test fun `optimistic replies join their parent immediately`() {
        val pending = item("local_reply", "parent").copy(deliveryState = CommentDeliveryState.Sending,
            localId = "client-id")
        val rows = flattenComments(listOf(item("parent"), item("other"), pending))
        assertEquals(listOf("parent", "local_reply", "other"), rows.map { it.item.id })
        assertEquals("parent", rows[1].rootId)
        assertTrue(rows[1].isLast)
    }

    @Test fun `connectors continue through descendants until the last sibling`() {
        val rows = flattenComments(listOf(item("root"), item("a", "root", 1),
            item("a1", "a", 2), item("a2", "a", 3), item("b", "root", 4), item("other")))
        assertEquals(listOf("root", "a", "a1", "a2", "b", "other"), rows.map { it.item.id })
        assertEquals(listOf(emptyList(), listOf(1), listOf(1, 2), listOf(1, 2), listOf(1), emptyList<Int>()),
            rows.map { it.linesAbove })
        assertEquals(listOf(listOf(1), listOf(1, 2), listOf(1, 2), listOf(1), emptyList(), emptyList<Int>()),
            rows.map { it.linesBelow })
    }

    @Test fun `last child ends parent connector even when it has children`() {
        val rows = flattenComments(listOf(item("root"), item("child", "root"), item("grandchild", "child")))
        assertEquals(listOf(1), rows[1].linesAbove)
        assertEquals(listOf(2), rows[1].linesBelow)
        assertEquals(listOf(2), rows[2].linesAbove)
        assertTrue(rows[2].linesBelow.isEmpty())
    }

    @Test fun `connectors cross deleted parents and stop at visible indentation limit`() {
        val items = (0..8).map { item("$it", if (it == 0) null else "${it - 1}") }
        val rows = flattenComments(withDeletedComment(items, "1"))
        assertEquals(listOf(1), rows[1].linesAbove)
        assertEquals(listOf(2), rows[1].linesBelow)
        assertTrue(rows.all { row -> (row.linesAbove + row.linesBelow).all { it in 1..MAX_COMMENT_INDENT } })
        assertTrue(rows.filter { it.depth > MAX_COMMENT_INDENT }.all { it.linesAbove.isEmpty() && it.linesBelow.isEmpty() })
    }

    @Test fun `connector data stays continuous between recycled rows`() {
        val rows = flattenComments(listOf(item("root"), item("a", "root", 1),
            item("nested", "a", 2), item("last", "root", 3)))
        rows.zipWithNext().forEach { (previous, next) ->
            assertEquals(previous.linesBelow, next.linesAbove)
        }
        assertTrue(rows.first().linesAbove.isEmpty())
        assertTrue(rows.last().linesBelow.isEmpty())
    }
}
