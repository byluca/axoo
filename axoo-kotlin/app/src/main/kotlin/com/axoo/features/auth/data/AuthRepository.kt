package com.axoo.features.auth.data

import com.axoo.core.session.SupabaseAuthApi
import com.axoo.core.session.SupabaseOtpRequest
import com.axoo.core.session.SupabaseVerifyOtpRequest
import com.axoo.core.session.SupabasePasswordRequest
import com.axoo.core.session.SupabaseSessionResponse

import com.axoo.BuildConfig
import com.axoo.core.push.PushRegistration
import com.axoo.core.session.SupabaseSessionStore
import com.axoo.features.auth.data.dto.EmailEligibilityRequest
import com.axoo.features.auth.data.dto.SessionBootstrapResponse
import com.axoo.features.profile.data.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class ProfileBootstrapException(cause: Throwable) : Exception(cause)

@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApi,
    private val supabaseAuthApi: SupabaseAuthApi,
    private val sessionStore: SupabaseSessionStore,
    private val profileRepository: ProfileRepository,
    private val pushRegistration: PushRegistration,
) {
    private val sessionChangeLock = Mutex()
    @Volatile var logoutPending = false
        private set

    suspend fun deleteAccount() = profileRepository.requestAccountDeletion()

    suspend fun emailEligibility(email: String): com.axoo.features.auth.data.dto.EmailEligibilityResponse =
        api.checkEmailEligibility(EmailEligibilityRequest(email))

    suspend fun signInAlpha(email: String, password: String): SessionBootstrapResponse {
        val generation = loginGeneration()
        val session = api.signInAlpha(com.axoo.features.auth.data.dto.AlphaLoginRequest(email, password))
        return establishSession(session, generation)
    }

    suspend fun sendOtp(email: String) {
        require(email.isNotBlank()) { "Inserisci il tuo indirizzo email." }
        supabaseAuthApi.sendEmailOtp(BuildConfig.SUPABASE_PUBLISHABLE_KEY, SupabaseOtpRequest(email))
    }

    suspend fun verifyOtp(email: String, code: String): SessionBootstrapResponse {
        require(code.length == 6) { "Il codice deve avere 6 cifre." }
        val generation = loginGeneration()
        val session = supabaseAuthApi.verifyEmailOtp(
            BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            SupabaseVerifyOtpRequest(email, code),
        )
        return establishSession(session, generation)
    }

    suspend fun signInWithPassword(email: String, password: String): SessionBootstrapResponse {
        val generation = loginGeneration()
        val session = supabaseAuthApi.signInWithPassword(
            BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            request = SupabasePasswordRequest(email, password),
        )
        return establishSession(session, generation)
    }

    private fun loginGeneration(): Long {
        if (logoutPending) throw CancellationException("Logout in corso")
        return sessionStore.generation
    }

    private fun requireGeneration(generation: Long) {
        if (logoutPending || sessionStore.generation != generation) {
            throw CancellationException("Sessione superata")
        }
    }

    private suspend fun establishSession(session: SupabaseSessionResponse, expectedGeneration: Long): SessionBootstrapResponse {
        val generation = sessionChangeLock.withLock {
            requireGeneration(expectedGeneration)
            profileRepository.clearCache()
            requireGeneration(expectedGeneration)
            sessionStore.save(session, expectedGeneration).also {
                requireGeneration(it)
            }
        }
        return try {
            api.createSession().also { requireGeneration(generation) }
        } catch (error: Throwable) {
            withContext(NonCancellable) { sessionStore.clearIfGeneration(generation) }
            if (error is CancellationException) throw error
            throw ProfileBootstrapException(error)
        }
    }

    suspend fun bootstrapSession(): SessionBootstrapResponse {
        val generation = sessionStore.generation
        requireGeneration(generation)
        return try {
            api.createSession().also { requireGeneration(generation) }
        } catch (error: HttpException) {
            if (error.code() == 401) sessionStore.clearIfGeneration(generation)
            throw error
        }
    }

    suspend fun acceptTerms(version: String): SessionBootstrapResponse {
        val generation = sessionStore.generation
        requireGeneration(generation)
        api.acceptTerms(com.axoo.features.auth.data.dto.AcceptTermsRequest(version))
        requireGeneration(generation)
        return bootstrapSession()
    }

    suspend fun acknowledgeModerationNotice(id: String) {
        api.acknowledgeModerationNotice(id)
    }

    @Synchronized
    fun beginLogout() {
        if (logoutPending) return
        logoutPending = true
        pushRegistration.stop()
        sessionStore.invalidate()
    }

    // Clears the local session and profile cache before completing logout.
    suspend fun logout(): String? {
        beginLogout()
        return withContext(NonCancellable) {
            sessionChangeLock.withLock {
                val oldAccessToken = try {
                    sessionStore.clear()
                } finally {
                    profileRepository.clearCache()
                }
                pushRegistration.unregister(oldAccessToken)
                oldAccessToken
            }.also { logoutPending = false }
        }
    }

    suspend fun isLoggedIn(): Boolean = sessionStore.hasSession()

    suspend fun recordActivityOpen() = api.recordActivityOpen()
}
