package com.axoo.features.notifications.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface NotificationsApi {
    @GET("notifications")
    suspend fun getNotifications(@Query("cursor") cursor: String? = null): NotificationsPage

    @GET("notifications/unread-count")
    suspend fun getNotificationsUnreadCount(): NotificationsCount

    @POST("notifications/seen")
    suspend fun markNotificationsSeen(@Body request: NotificationsSeenRequest): NotificationsCount
}
