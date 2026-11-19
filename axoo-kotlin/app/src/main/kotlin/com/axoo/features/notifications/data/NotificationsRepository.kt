package com.axoo.features.notifications.data

import com.axoo.core.session.SupabaseSessionStore
import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.engine.emoji.text.toUiEntityOffsets
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@JsonClass(generateAdapter = true)
data class AppNotification(
    val id: String,
    @Json(name = "actor_id") val actorId: String,
    @Json(name = "post_id") val postId: String,
    @Json(name = "comment_id") val commentId: String,
    val kind: String,
    val nickname: String,
    val message: String,
    val preview: String = "",
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "seen_at") val seenAt: String? = null,
    @Json(name = "preview_entities") val previewEntities: List<MessageEntity> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class NotificationsPage(
    val notifications: List<AppNotification>,
    @Json(name = "next_cursor") val nextCursor: String? = null,
    @Json(name = "seen_through") val seenThrough: String? = null,
    @Json(name = "unread_count") val unreadCount: Int = 0,
)

@JsonClass(generateAdapter = true)
data class NotificationsCount(@Json(name = "unread_count") val unreadCount: Int)

@JsonClass(generateAdapter = true)
data class NotificationsSeenRequest(@Json(name = "seen_through") val seenThrough: String)

@Singleton
class NotificationsRepository @Inject constructor(
    private val api: NotificationsApi,
    private val session: SupabaseSessionStore,
) {
    private val _unread = MutableStateFlow(0)
    val unread = _unread.asStateFlow()
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes = _changes.asSharedFlow()
    private var sessionGeneration = session.generation
    private var revision = 0L

    @Synchronized
    fun prepareSession() {
        if (sessionGeneration == session.generation) return
        sessionGeneration = session.generation
        revision++
        _unread.value = 0
    }

    private suspend fun checkSession(expected: Long) {
        currentCoroutineContext().ensureActive()
        if (session.generation != expected) throw CancellationException("Sessione terminata")
    }

    @Synchronized
    private fun requestVersion(increment: Boolean = false): Pair<Long, Long> {
        prepareSession()
        if (increment) revision++
        return sessionGeneration to revision
    }

    @Synchronized
    private fun applyCount(expected: Pair<Long, Long>, count: Int) {
        if (session.generation == expected.first && sessionGeneration == expected.first && revision == expected.second) {
            _unread.value = count.coerceAtLeast(0)
        }
    }

    suspend fun page(cursor: String? = null): NotificationsPage {
        val expected = requestVersion()
        val page = api.getNotifications(cursor)
        checkSession(expected.first)
        applyCount(expected, page.unreadCount)
        return page.copy(notifications = page.notifications.map {
            it.copy(previewEntities = it.previewEntities.toUiEntityOffsets(it.preview))
        })
    }

    suspend fun refreshUnread() {
        val expected = requestVersion(increment = true)
        val count = api.getNotificationsUnreadCount()
        checkSession(expected.first)
        applyCount(expected, count.unreadCount)
    }

    suspend fun seen(through: String) {
        val expected = requestVersion(increment = true)
        val count = api.markNotificationsSeen(NotificationsSeenRequest(through))
        checkSession(expected.first)
        applyCount(expected, count.unreadCount)
    }

    suspend fun received() {
        _changes.emit(Unit)
        try { refreshUnread() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { /* Delivery remains usable when the inbox cannot refresh. */ }
    }
}
