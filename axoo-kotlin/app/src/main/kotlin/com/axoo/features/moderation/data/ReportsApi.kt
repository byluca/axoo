package com.axoo.features.moderation.data

import retrofit2.http.Body
import retrofit2.http.POST

interface ReportsApi {
    @POST("reports")
    suspend fun report(@Body request: ReportRequest)
}
