package com.axoo.features.auth.presentation

import com.axoo.features.auth.data.AuthRepository
import com.axoo.features.auth.data.dto.SessionBootstrapResponse
import javax.inject.Inject

/** Session operations contain no screen state, jobs or observable flows. */
class AuthOperations @Inject constructor(private val repository: AuthRepository) {
    val logoutPending: Boolean get() = repository.logoutPending

    suspend fun restore(): SessionBootstrapResponse? {
        if (!repository.isLoggedIn()) return null
        val account = repository.bootstrapSession()
        require(account.status in setOf("active", "suspended", "banned")) {
            "Stato dell'account non riconosciuto."
        }
        return account
    }

    suspend fun acceptTerms(version: String) = repository.acceptTerms(version)
    suspend fun recordActivityOpen() = repository.recordActivityOpen()
    suspend fun deleteAccount() = repository.deleteAccount()
    suspend fun hasSession(): Boolean = repository.isLoggedIn()
    fun beginLogout() = repository.beginLogout()
    suspend fun signOut() { repository.logout() }
    suspend fun acknowledgeModerationNotice(id: String) = repository.acknowledgeModerationNotice(id)
}
