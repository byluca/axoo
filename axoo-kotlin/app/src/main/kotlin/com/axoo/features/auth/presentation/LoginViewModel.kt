package com.axoo.features.auth.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axoo.features.auth.data.AuthRepository
import com.axoo.features.auth.data.ProfileBootstrapException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException

/** Coordinates one login attempt; it contains no visual decisions. */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val checkEmailEligibility: CheckLoginEmailEligibilityUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()
    private var resendCooldownJob: Job? = null

    fun reset() {
        resendCooldownJob?.cancel()
        _uiState.value = LoginUiState()
    }

    fun requireTerms(account: com.axoo.features.auth.data.dto.SessionBootstrapResponse) {
        reset()
        _uiState.value = LoginUiState(termsAccount = account)
    }

    fun onTermsAcceptedChanged(accepted: Boolean) {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(termsAccepted = accepted)
    }

    fun onEmailChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            email = value,
            error = null,
            info = null,
            isReviewAccess = false,
        )
    }

    fun onContinueClicked() {
        val state = _uiState.value
        if (state.isLoading || !state.termsAccepted) return
        if (state.step == LoginStep.PASSWORD) {
            signInWithPassword()
            return
        }
        state.termsAccount?.let { account ->
            _uiState.value = state.copy(isVerifying = true, error = null)
            viewModelScope.launch { completeLogin(account) }
            return
        }
        if (state.resendSeconds > 0) {
            _uiState.value = state.copy(error = "Attendi ${state.resendSeconds}s prima di riprovare.")
            return
        }
        val email = state.email.trim().lowercase()
        if (email == PLAY_REVIEW_EMAIL) {
            _uiState.value = state.copy(
                email = email,
                step = LoginStep.OTP,
                code = "",
                info = "Inserisci il codice revisore fornito in Google Play Console.",
                error = null,
                isReviewAccess = true,
            )
            return
        }
        checkEmailAndSendOtp(email)
    }

    fun onOtpChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            code = value.filter(Char::isDigit).take(OTP_LENGTH),
            error = null,
        )
        if (_uiState.value.step == LoginStep.OTP && _uiState.value.code.length == OTP_LENGTH) onVerifyOtpClicked()
    }

    fun onPasswordChanged(value: String) {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(password = value.take(128), error = null)
    }

    private fun signInWithPassword() {
        val state = _uiState.value
        if (state.password.isEmpty()) return
        _uiState.value = state.copy(isVerifying = true, error = null)
        viewModelScope.launch {
            try {
                val bootstrap = if (state.accountPassword) authRepository.signInWithPassword(state.email.trim().lowercase(), state.password)
                    else authRepository.signInAlpha(state.email.trim().lowercase(), state.password)
                _uiState.value = _uiState.value.copy(password = "")
                completeLogin(bootstrap)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(isVerifying = false, error = when {
                    error is HttpException && error.code() in listOf(400, 401, 422) -> "Email o password non validi."
                    error is HttpException && error.code() == 429 -> "Troppi tentativi. Attendi un minuto e riprova."
                    else -> "Accesso non disponibile. Riprova tra poco."
                })
                emitInputRejected(LoginStep.PASSWORD)
            }
        }
    }

    fun onVerifyOtpClicked() {
        val state = _uiState.value
        if (state.isLoading || !state.termsAccepted || state.code.length != OTP_LENGTH) return
        verifyOtp(state.code)
    }

    fun onResendClicked() {
        val state = _uiState.value
        if (state.isReviewAccess || state.termsAccount != null) return
        if (state.isLoading || state.resendSeconds > 0) return
        sendOtp(state.email.trim().lowercase(), isResend = true)
    }

    fun onBackClicked() {
        val state = _uiState.value
        if (state.step != LoginStep.EMAIL) {
            resendCooldownJob?.cancel()
            _uiState.value = state.copy(
                step = LoginStep.EMAIL,
                code = "",
                password = "",
                error = null,
                info = null,
                resendSeconds = 0,
                isReviewAccess = false,
            )
        }
    }

    private fun sendOtp(email: String, isResend: Boolean) {
        val state = _uiState.value
        if (state.isSending || state.resendSeconds > 0) return
        _uiState.value = state.copy(isSending = true, error = null, info = null)
        viewModelScope.launch {
            runOtpRequest { authRepository.sendOtp(email) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        step = LoginStep.OTP,
                        code = if (isResend) "" else _uiState.value.code,
                        isSending = false,
                        info = if (isResend) "Nuovo codice inviato" else null,
                    )
                    startResendCooldown()
                    _events.emit(LoginEvent.OtpSent)
                }
                .onFailure { error ->
                    Log.w(TAG, "OTP email request failed (${error.javaClass.simpleName})")
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        error = otpSendErrorMessage(error),
                        info = null,
                    )
                    startResendCooldown(RETRY_COOLDOWN_SECONDS)
                    emitInputRejected(_uiState.value.step)
                }
        }
    }

    private fun checkEmailAndSendOtp(email: String) {
        val state = _uiState.value
        _uiState.value = state.copy(isSending = true, error = null, info = null)
        viewModelScope.launch {
            runOtpRequest {
                val eligibility = checkEmailEligibility(email)
                if (!eligibility.eligible) throw EmailNotEligibleException
                val password = eligibility.loginMethod in setOf("password", "account_password")
                if (!password) authRepository.sendOtp(email)
                eligibility.loginMethod
            }
                .onSuccess { loginMethod ->
                    val password = loginMethod in setOf("password", "account_password")
                    _uiState.value = _uiState.value.copy(
                        step = if (password) LoginStep.PASSWORD else LoginStep.OTP,
                        accountPassword = loginMethod == "account_password",
                        code = "",
                        password = "",
                        isSending = false,
                        error = null,
                    )
                    if (!password) {
                        startResendCooldown()
                        _events.emit(LoginEvent.OtpSent)
                    }
                }
                .onFailure { error ->
                    Log.w(TAG, "Email eligibility or OTP request failed (${error.javaClass.simpleName})")
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        error = if (error === EmailNotEligibleException) {
                            "Usa l'email universitaria del tuo campus"
                        } else {
                            otpSendErrorMessage(error)
                        },
                        info = null,
                    )
                    if (error !== EmailNotEligibleException) {
                        startResendCooldown(RETRY_COOLDOWN_SECONDS)
                    }
                    emitInputRejected(LoginStep.EMAIL)
                }
        }
    }

    private suspend fun <T> runOtpRequest(block: suspend () -> T): Result<T> = try {
        Result.success(withTimeout(OTP_REQUEST_TIMEOUT_MS) { block() })
    } catch (error: TimeoutCancellationException) {
        Result.failure(error)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private fun verifyOtp(code: String) {
        val state = _uiState.value
        if (state.isLoading) return
        viewModelScope.launch {
            _uiState.value = state.copy(isVerifying = true, error = null)
            runCatching {
                if (state.isReviewAccess) {
                    authRepository.signInWithPassword(state.email.trim().lowercase(), code)
                } else {
                    authRepository.verifyOtp(state.email.trim().lowercase(), code)
                }
            }
                .onSuccess { bootstrap -> completeLogin(bootstrap) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = _uiState.value.copy(
                        isVerifying = false,
                        error = when {
                            error is ProfileBootstrapException ->
                                "Il codice è valido, ma non siamo riusciti a creare il profilo. Riprova tra poco."

                            error is HttpException && error.code() == 403 -> "Codice non valido, riprova."
                            else -> "Codice non valido o scaduto."
                        },
                    )
                    emitInputRejected(LoginStep.OTP)
                }
        }
    }

    private suspend fun completeLogin(initial: com.axoo.features.auth.data.dto.SessionBootstrapResponse) {
        var account = initial
        if (account.status == "active" && account.termsRequired) {
            _uiState.value = _uiState.value.copy(termsAccount = account)
            try {
                account = authRepository.acceptTerms(account.termsVersion)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                _uiState.value = _uiState.value.copy(step = LoginStep.EMAIL, code = "", isVerifying = false,
                    error = "Non riusciamo a salvare l’accettazione. Riprova.")
                return
            }
        }
        _events.emit(LoginEvent.LoginSucceeded(account))
    }

    private fun startResendCooldown(seconds: Int = RESEND_COOLDOWN_SECONDS) {
        resendCooldownJob?.cancel()
        _uiState.value = _uiState.value.copy(resendSeconds = seconds)
        resendCooldownJob = viewModelScope.launch {
            for (remaining in (seconds - 1) downTo 0) {
                delay(1_000L)
                _uiState.value = _uiState.value.copy(resendSeconds = remaining)
            }
        }
    }

    private fun emitInputRejected(step: LoginStep) {
        viewModelScope.launch { _events.emit(LoginEvent.InputRejected(step)) }
    }

    private fun otpSendErrorMessage(error: Throwable): String = when (error) {
        is TimeoutCancellationException -> "Il server non risponde. Riprova tra pochi secondi."
        is IOException -> "Controlla la connessione e riprova tra pochi secondi."
        is HttpException -> when (error.code()) {
            429 -> "Hai richiesto troppi codici. Attendi qualche minuto e riprova."
            in 500..599 -> "Il servizio email è momentaneamente non disponibile. Riprova più tardi."
            else -> "Non siamo riusciti a inviare l'email. Riprova più tardi."
        }

        else -> "Non siamo riusciti a inviare l'email. Riprova più tardi."
    }
}

data class LoginUiState(
    val step: LoginStep = LoginStep.EMAIL,
    val email: String = "",
    val code: String = "",
    val password: String = "",
    val accountPassword: Boolean = false,
    val isSending: Boolean = false,
    val isVerifying: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val resendSeconds: Int = 0,
    val isReviewAccess: Boolean = false,
    val termsAccepted: Boolean = false,
    val termsAccount: com.axoo.features.auth.data.dto.SessionBootstrapResponse? = null,
) {
    val isLoading: Boolean get() = isSending || isVerifying
    val canSubmit: Boolean get() = !isLoading && termsAccepted && when (step) {
        LoginStep.OTP -> code.length == OTP_LENGTH
        LoginStep.PASSWORD -> password.isNotEmpty()
        LoginStep.EMAIL -> termsAccount != null || email.isNotBlank() && resendSeconds == 0
    }
    val canResend: Boolean get() = step == LoginStep.OTP && !isLoading && termsAccount == null && resendSeconds == 0
}

enum class LoginStep { EMAIL, OTP, PASSWORD }
sealed interface LoginEvent {
    data class LoginSucceeded(val bootstrap: com.axoo.features.auth.data.dto.SessionBootstrapResponse) : LoginEvent
    data object OtpSent : LoginEvent
    data class InputRejected(val step: LoginStep) : LoginEvent
}

private const val OTP_LENGTH = 6
private const val RESEND_COOLDOWN_SECONDS = 30
private const val RETRY_COOLDOWN_SECONDS = 5
private const val OTP_REQUEST_TIMEOUT_MS = 20_000L
private const val TAG = "LoginViewModel"
const val PLAY_REVIEW_EMAIL = "google-play-review@studenti.poliba.it"

private object EmailNotEligibleException : IllegalArgumentException()
