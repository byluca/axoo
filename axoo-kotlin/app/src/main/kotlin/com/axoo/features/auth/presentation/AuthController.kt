package com.axoo.features.auth.presentation

import com.axoo.features.auth.data.dto.SessionBootstrapResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** The Activity owns this controller and its scope; each operation routes the UI directly. */
class AuthController(
    private val operations: AuthOperations,
    private val scope: CoroutineScope,
    private val onChecking: () -> Unit,
    private val onLoggedOut: () -> Unit,
    private val onAccount: (SessionBootstrapResponse) -> Unit,
    private val onLogout: (failed: Boolean) -> Unit,
    private val onRestoreFailed: () -> Unit,
) {
    enum class DeletionFailure { InvalidConfirmation, RequestFailed }
    private var deleteJob: Job? = null
    val isDeletingAccount get() = deleteJob?.isActive == true
    private var account: SessionBootstrapResponse? = null
    private var refreshJob: Job? = null
    private var logoutJob: Job? = null
    private var generation = 0L
    private var logoutRequested = operations.logoutPending
    private var loggedOut = false

    fun refreshSessionStatus() {
        if (isDeletingAccount) return
        if (logoutRequested) {
            if (logoutJob == null) onLogout(true)
            return
        }
        if (loggedOut) return
        val request = ++generation
        refreshJob?.cancel()
        if (account == null) onChecking()
        refreshJob = scope.launch {
            try {
                val restored = operations.restore()
                ensureActive()
                if (request != generation) return@launch
                if (restored == null) showLoggedOut() else showAccount(restored)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (request != generation) return@launch
                val hasSession = try {
                    operations.hasSession()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) { true }
                ensureActive()
                if (request != generation) return@launch
                if (!hasSession) showLoggedOut()
                else if (account == null) onRestoreFailed()
                // A failed background check leaves the already mounted account in place.
            }
        }
    }

    fun logout() {
        if (logoutJob?.isActive == true || loggedOut) return
        ++generation
        logoutRequested = true
        operations.beginLogout()
        account = null
        refreshJob?.cancel()
        onLogout(false)
        logoutJob = scope.launch {
            try {
                operations.signOut()
                ensureActive()
                showLoggedOut()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ensureActive()
                onLogout(true)
            }
        }
    }

    fun onAuthenticated(bootstrap: SessionBootstrapResponse) {
        // Ignore a login callback queued before logout started.
        if (logoutRequested) return
        ++generation
        refreshJob?.cancel()
        showAccount(bootstrap)
    }

    fun deleteRestrictedAccount(confirmation: String, onFailure: (DeletionFailure) -> Unit) {
        if (!confirmation.trim().equals("ELIMINA", ignoreCase = true)) {
            onFailure(DeletionFailure.InvalidConfirmation)
            return
        }
        if (deleteJob?.isActive == true || loggedOut || logoutRequested) return
        val expected = ++generation
        refreshJob?.cancel()
        deleteJob = scope.launch {
            try {
                operations.deleteAccount()
                ensureActive()
                if (generation == expected) logout()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (generation == expected) onFailure(DeletionFailure.RequestFailed) }
        }
    }

    fun acknowledgeModerationNotice(id: String) {
        val current = account ?: return
        if (current.moderationNotice?.id != id) return
        account = current.copy(moderationNotice = null)
        scope.launch {
            try { operations.acknowledgeModerationNotice(id) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* The server can present the notice again next time. */ }
        }
    }

    private fun showAccount(bootstrap: SessionBootstrapResponse) {
        loggedOut = false
        account = bootstrap
        if (bootstrap.status == "active" && !bootstrap.androidLocked && !bootstrap.termsRequired) scope.launch {
            try { operations.recordActivityOpen() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { }
        }
        onAccount(bootstrap)
    }

    private fun showLoggedOut() {
        account = null
        loggedOut = true
        onLoggedOut()
    }
}
