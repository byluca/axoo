package com.axoo.features.profile.data

import com.axoo.features.profile.data.dto.BlockedUsersResponse
import com.axoo.features.profile.data.dto.DeleteAccountRequest
import com.axoo.features.profile.data.dto.NicknameAvailabilityResponse
import com.axoo.features.profile.data.dto.UpdateAvatarRequest
import com.axoo.features.profile.data.dto.UpdateBioRequest
import com.axoo.features.profile.data.dto.UpdateNicknameRequest
import com.axoo.features.profile.domain.PublicProfile
import com.axoo.features.profile.domain.User
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ProfileApi {
    @GET("users/me")
    suspend fun getMe(): User

    @GET("users/{userId}")
    suspend fun getPublicProfile(@Path("userId") userId: String): PublicProfile

    @PUT("users/me/privacy")
    suspend fun updateProfilePrivacy(@Body request: Map<String, Boolean>): User

    @PUT("users/me/bio")
    suspend fun updateBio(@Body request: UpdateBioRequest): User

    @GET("users/me/nickname-availability")
    suspend fun getNicknameAvailability(
        @Query("nickname") nickname: String,
    ): NicknameAvailabilityResponse

    @PUT("users/me/nickname")
    suspend fun updateNickname(@Body request: UpdateNicknameRequest): User

    @PUT("users/avatar")
    suspend fun updateAvatar(@Body request: UpdateAvatarRequest)

    @PUT("users/blocks/{userId}")
    suspend fun blockUser(@Path("userId") userId: String)

    @GET("users/blocks")
    suspend fun getBlockedUsers(): BlockedUsersResponse

    @DELETE("users/blocks/{userId}")
    suspend fun unblockUser(@Path("userId") userId: String)

    @HTTP(method = "DELETE", path = "users/me", hasBody = true)
    suspend fun deleteAccount(@Body request: DeleteAccountRequest)
}
