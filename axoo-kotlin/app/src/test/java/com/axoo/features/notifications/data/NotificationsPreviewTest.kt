package com.axoo.features.notifications.data

import com.axoo.core.session.SupabaseSessionStore
import com.axoo.engine.emoji.text.MessageEntity
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsPreviewTest {
    private val json = """{"id":"1","actor_id":"actor","post_id":"post","comment_id":"comment","kind":"post_comment","nickname":"Giulia","message":"Giulia ha commentato il tuo post","preview":"😀\n👩‍🎓❤️","created_at":"2026-10-16T10:00:00Z"}"""
    private val adapter = Moshi.Builder().build().adapter(AppNotification::class.java)

    @Test fun `old notifications without entities keep their fallback text`() {
        val item = requireNotNull(adapter.fromJson(json))
        assertTrue(item.previewEntities.isEmpty())
        assertEquals("😀\n👩‍🎓❤️", item.preview)
    }

    @Test fun `server code point offsets become UTF16 offsets before rendering`() = runTest {
        val item = requireNotNull(adapter.fromJson(json)).copy(
            previewEntities = listOf(MessageEntity(offset = 5, length = 2, customEmojiId = 42)),
        )
        val api = mockk<NotificationsApi>()
        val session = mockk<SupabaseSessionStore> { every { generation } returns 1L }
        coEvery { api.getNotifications(null) } returns NotificationsPage(listOf(item))
        val preview = NotificationsRepository(api, session).page().notifications.single()
        val entity = preview.previewEntities.single()
        assertEquals(42, entity.customEmojiId)
        assertEquals(8, entity.offset)
        assertEquals("❤️", preview.preview.substring(entity.offset, entity.offset + entity.length))
    }
}
