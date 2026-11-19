package com.axoo.features.moderation.data

import com.axoo.features.moderation.data.dto.ModerationActionRequest
import com.axoo.features.moderation.data.dto.ModerationQueueItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModerationRepository @Inject constructor(
    private val api: ModerationApi,
) {
    suspend fun queue(status: String): List<ModerationQueueItem> =
        api.getModerationQueue(status).items

    suspend fun act(
        action: String,
        targetType: String,
        targetId: String,
        reason: String = "",
        durationHours: Int = 0,
        sourceTargetType: String? = null,
        sourceTargetId: String? = null,
    ) {
        api.moderate(
            ModerationActionRequest(
                action = action,
                targetType = targetType,
                targetId = targetId,
                reason = reason,
                durationHours = durationHours,
                sourceTargetType = sourceTargetType,
                sourceTargetId = sourceTargetId,
            ),
        )
    }
}
