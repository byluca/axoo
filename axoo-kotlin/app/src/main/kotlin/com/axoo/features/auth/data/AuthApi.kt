package com.axoo.features.auth.data

import com.axoo.core.session.SupabaseSessionResponse
import com.axoo.features.auth.data.dto.AcceptTermsRequest
import com.axoo.features.auth.data.dto.AlphaLoginRequest
import com.axoo.features.auth.data.dto.EmailEligibilityRequest
import com.axoo.features.auth.data.dto.EmailEligibilityResponse
import com.axoo.features.auth.data.dto.SessionBootstrapResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface AuthApi {
    @POST("auth/email-eligibility")
    suspend fun checkEmailEligibility(
        @Body request: EmailEligibilityRequest,
    ): EmailEligibilityResponse

    @POST("auth/alpha-session")
    suspend fun signInAlpha(
        @Body request: AlphaLoginRequest,
    ): SupabaseSessionResponse

    @POST("auth/session")
    suspend fun createSession(): SessionBootstrapResponse

    @PUT("users/me/terms")
    suspend fun acceptTerms(@Body request: AcceptTermsRequest)

    @POST("users/me/moderation-notices/{id}/seen")
    suspend fun acknowledgeModerationNotice(@Path("id") id: String)

    @POST("activity/open")
    suspend fun recordActivityOpen()
}
