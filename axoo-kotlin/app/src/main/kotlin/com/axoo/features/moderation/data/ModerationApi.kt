package com.axoo.features.moderation.data

import com.axoo.features.moderation.data.dto.ModerationActionRequest
import com.axoo.features.moderation.data.dto.ModerationQueueResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ModerationApi {
    @GET("moderation/queue")
    suspend fun getModerationQueue(
        @Query("status") status: String,
    ): ModerationQueueResponse

    @POST("moderation/actions")
    suspend fun moderate(@Body request: ModerationActionRequest)
}
