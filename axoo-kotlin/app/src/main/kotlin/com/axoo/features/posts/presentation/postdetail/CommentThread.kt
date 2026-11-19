package com.axoo.features.posts.presentation.postdetail

import com.axoo.features.posts.domain.Comment
import java.time.Instant

internal const val MAX_COMMENT_INDENT = 4
internal const val COMMENT_INDENT_DP = 14f

/** Keep only tombstones that still connect a visible reply to its ancestors. */
internal fun withDeletedComment(comments: List<CommentUiModel>, id: String): List<CommentUiModel> {
    val updated = comments.map { item ->
        if (item.id != id) item else item.copy(comment = item.comment.copy(
            isDeleted = true, body = "", media = null, entities = emptyList(), userId = "", nickname = "",
            avatarConfig = null, universityName = "", upvotes = 0, downvotes = 0, myVoteRaw = null,
        ))
    }
    val byId = updated.associateBy { it.id }
    val retained = hashSetOf<String>()
    updated.filterNot { it.comment.isDeleted }.forEach { item ->
        var current: CommentUiModel? = item
        while (current != null && retained.add(current.id)) {
            current = byId[current.comment.parentCommentId]
        }
    }
    return updated.filter { it.id in retained }
}

internal data class FlattenedComment(
    val item: CommentUiModel,
    val depth: Int = 0,
    val rootId: String = item.stableKey,
    val isFirst: Boolean = true,
    val isLast: Boolean = true,
    val linesAbove: List<Int> = emptyList(),
    val linesBelow: List<Int> = emptyList(),
) {
    val comment: Comment get() = item.comment
}

/** Root order comes from Hot/New; replies are chronological within each branch. */
internal fun flattenComments(comments: List<CommentUiModel>): List<FlattenedComment> {
    val byId = comments.associateBy { it.id }
    val children = comments.groupBy { it.comment.parentCommentId }
    val orderedChildren = children.mapValues { (_, items) ->
        items.sortedWith(compareBy<CommentUiModel> {
            runCatching { Instant.parse(it.comment.createdAt) }.getOrDefault(Instant.EPOCH)
        }.thenBy { it.stableKey })
    }
    val roots = comments.filter { it.comment.parentCommentId !in byId }
    val result = ArrayList<FlattenedComment>(comments.size)
    val visited = hashSetOf<String>()
    fun appendThread(root: CommentUiModel) {
        if (root.id in visited) return
        val start = result.size
        val stack = ArrayDeque<Pair<CommentUiModel, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty()) {
            val (item, depth) = stack.removeLast()
            if (!visited.add(item.id)) continue
            result += FlattenedComment(item, depth, root.stableKey, result.size == start, false)
            orderedChildren[item.id].orEmpty().asReversed().forEach { stack.addLast(it to depth + 1) }
        }
        result[result.lastIndex] = result.last().copy(isLast = true)
        // Carry each branch back to its parent, stopping after its last child.
        // Store geometry per row so connections survive RecyclerView recycling.
        val activeLines = mutableSetOf<Int>()
        for (index in result.lastIndex downTo start) {
            val row = result[index]
            val below = activeLines.sorted()
            activeLines.removeAll { it > row.depth }
            if (row.depth in 1..MAX_COMMENT_INDENT) activeLines.add(row.depth)
            result[index] = row.copy(linesAbove = activeLines.sorted(), linesBelow = below)
        }
    }
    roots.forEach(::appendThread)
    // Defensive fallback for malformed cycles: never lose comments or loop forever.
    comments.forEach(::appendThread)
    return result
}
