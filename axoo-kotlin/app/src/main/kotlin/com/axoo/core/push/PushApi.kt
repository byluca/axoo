package com.axoo.core.push

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path

interface PushApi {
    @PUT("users/me/push-installations/{installationId}")
    suspend fun registerPushInstallation(
        @Path("installationId") installationId: String,
        @Body request: PushRegistrationRequest,
        @Header("Authorization") authorization: String,
    )

    @HTTP(method = "DELETE", path = "users/me/push-installations/{installationId}", hasBody = true)
    suspend fun removePushInstallation(
        @Path("installationId") installationId: String,
        @Body request: PushRegistrationRequest,
        @Header("Authorization") authorization: String,
    )
}
