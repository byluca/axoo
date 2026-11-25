package com.axoo.features.auth.presentation

import android.util.Log
import com.axoo.features.auth.data.AuthRepository
import com.axoo.features.auth.data.dto.EmailEligibilityResponse
import com.axoo.features.auth.data.ProfileBootstrapException
import com.axoo.testing.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUpAndroidLog() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun tearDownAndroidLog() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `back from otp returns to email and back from email stays there`() = runTest {
        val model = LoginViewModel(mockk(relaxed = true))
        model.onEmailChanged(PLAY_REVIEW_EMAIL)
        model.onTermsAcceptedChanged(true)
        model.onContinueClicked()
        assertEquals(LoginStep.OTP, model.uiState.value.step)
        model.onBackClicked()
        assertEquals(LoginStep.EMAIL, model.uiState.value.step)
        assertEquals(PLAY_REVIEW_EMAIL, model.uiState.value.email)
        assertFalse(model.uiState.value.isReviewAccess)
        val emailState = model.uiState.value
        model.onBackClicked()
        advanceUntilIdle()
        assertEquals(emailState, model.uiState.value)
    }

    @Test
    fun `unchecked consent prevents sending email`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        val model = LoginViewModel(repository)
        model.onEmailChanged("student@example.it")
        model.onContinueClicked()
        advanceUntilIdle()
        assertFalse(model.uiState.value.canSubmit)
        coVerify(exactly = 0) { repository.emailEligibility(any()) }
        coVerify(exactly = 0) { repository.sendOtp(any()) }
        model.onTermsAcceptedChanged(true)
        assertTrue(model.uiState.value.canSubmit)
    }

    @Test
    fun `malformed email is rejected before calling the server`() = runTest {
        val repository = mockk<AuthRepository>()
        val model = LoginViewModel(repository)
        model.onEmailChanged("student@localhost")
        model.onTermsAcceptedChanged(true)
        model.onContinueClicked()
        advanceUntilIdle()

        assertEquals(LoginStep.EMAIL, model.uiState.value.step)
        assertEquals("Usa l'email universitaria del tuo campus", model.uiState.value.error)
        coVerify(exactly = 0) { repository.emailEligibility(any()) }
        coVerify(exactly = 0) { repository.sendOtp(any()) }
    }

    @Test
    fun `email consent is saved after otp before login success`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        val initial = com.axoo.features.auth.data.dto.SessionBootstrapResponse(termsRequired = true)
        coEvery { repository.emailEligibility("student@example.it") } returns EmailEligibilityResponse(true)
        coEvery { repository.verifyOtp("student@example.it", "123456") } returns initial
        coEvery { repository.acceptTerms(initial.termsVersion) } returns initial.copy(termsRequired = false)
        val model = LoginViewModel(repository)
        val events = mutableListOf<LoginEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.events.collect { events.add(it) } }
        model.onEmailChanged("student@example.it")
        model.onTermsAcceptedChanged(true)
        model.onContinueClicked()
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.acceptTerms(any()) }
        model.onOtpChanged("123456")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.acceptTerms(initial.termsVersion) }
        assertEquals(false, events.filterIsInstance<LoginEvent.LoginSucceeded>().single().bootstrap.termsRequired)
    }

    @Test
    fun `restored account requires consent and failed save retries without otp`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        val initial = com.axoo.features.auth.data.dto.SessionBootstrapResponse(termsRequired = true)
        coEvery { repository.acceptTerms(initial.termsVersion) } throws IOException("offline")
        val model = LoginViewModel(repository)
        val events = mutableListOf<LoginEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.events.collect { events.add(it) } }
        model.requireTerms(initial)
        model.onContinueClicked()
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.acceptTerms(any()) }
        model.onTermsAcceptedChanged(true)
        model.onContinueClicked()
        advanceUntilIdle()
        assertTrue(events.filterIsInstance<LoginEvent.LoginSucceeded>().isEmpty())
        assertEquals(initial, model.uiState.value.termsAccount)
        assertEquals(LoginStep.EMAIL, model.uiState.value.step)
        assertFalse(model.uiState.value.isLoading)
        coEvery { repository.acceptTerms(initial.termsVersion) } returns initial.copy(termsRequired = false)
        model.onContinueClicked()
        advanceUntilIdle()
        assertEquals(1, events.filterIsInstance<LoginEvent.LoginSucceeded>().size)
        coVerify(exactly = 0) { repository.verifyOtp(any(), any()) }
        coVerify(exactly = 0) { repository.sendOtp(any()) }
    }

    @Test
    fun `failed consent after otp retries without consuming code again`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        val initial = com.axoo.features.auth.data.dto.SessionBootstrapResponse(termsRequired = true)
        coEvery { repository.emailEligibility("student@example.it") } returns EmailEligibilityResponse(true)
        coEvery { repository.verifyOtp("student@example.it", "123456") } returns initial
        coEvery { repository.acceptTerms(initial.termsVersion) } throws IOException("offline")
        val model = LoginViewModel(repository)
        val events = mutableListOf<LoginEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.events.collect { events.add(it) } }
        model.onEmailChanged("student@example.it")
        model.onTermsAcceptedChanged(true)
        model.onContinueClicked()
        advanceUntilIdle()
        model.onOtpChanged("123456")
        advanceUntilIdle()
        assertEquals(LoginStep.EMAIL, model.uiState.value.step)
        assertTrue(events.filterIsInstance<LoginEvent.LoginSucceeded>().isEmpty())
        coEvery { repository.acceptTerms(initial.termsVersion) } returns initial.copy(termsRequired = false)
        model.onContinueClicked()
        advanceUntilIdle()
        assertEquals(1, events.filterIsInstance<LoginEvent.LoginSucceeded>().size)
        coVerify(exactly = 1) { repository.verifyOtp("student@example.it", "123456") }
        coVerify(exactly = 1) { repository.sendOtp("student@example.it") }
    }

    @Test
    fun `pending acceptance ignores duplicate submissions`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        val initial = com.axoo.features.auth.data.dto.SessionBootstrapResponse(termsRequired = true)
        val response = CompletableDeferred<com.axoo.features.auth.data.dto.SessionBootstrapResponse>()
        coEvery { repository.acceptTerms(any()) } coAnswers { response.await() }
        val model = LoginViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.events.collect {} }
        model.requireTerms(initial)
        model.onTermsAcceptedChanged(true)
        model.onContinueClicked()
        model.onContinueClicked()
        runCurrent()
        coVerify(exactly = 1) { repository.acceptTerms(initial.termsVersion) }
        response.complete(initial.copy(termsRequired = false))
        advanceUntilIdle()
    }

    @Test
    fun `valid otp with failed profile bootstrap shows actionable error`() = runTest {
        val authRepository = mockk<AuthRepository>()
        coEvery { authRepository.emailEligibility("studenti@example.it") } returns EmailEligibilityResponse(true)
        coEvery { authRepository.sendOtp("studenti@example.it") } returns Unit
        coEvery {
            authRepository.verifyOtp("studenti@example.it", "123456")
        } throws ProfileBootstrapException(IOException("profile conflict"))
        val viewModel = LoginViewModel(authRepository)

        viewModel.onEmailChanged("studenti@example.it")
        viewModel.onTermsAcceptedChanged(true)
        viewModel.onContinueClicked()
        advanceUntilIdle()
        viewModel.onOtpChanged("123456")
        advanceUntilIdle()

        assertEquals(
            "Il codice è valido, ma non siamo riusciti a creare il profilo. Riprova tra poco.",
            viewModel.uiState.value.error,
        )
    }

    @Test
    fun `otp request timeout always restores the send button`() = runTest {
        val authRepository = mockk<AuthRepository>()
        coEvery { authRepository.emailEligibility("studenti@example.it") } coAnswers { awaitCancellation() }
        val viewModel = LoginViewModel(authRepository)

        viewModel.onEmailChanged("studenti@example.it")
        viewModel.onTermsAcceptedChanged(true)
        viewModel.onContinueClicked()
        runCurrent()

        assertTrue(viewModel.uiState.value.isSending)

        advanceTimeBy(20_001L)
        runCurrent()

        assertFalse(viewModel.uiState.value.isSending)
        assertEquals(
            "Il server non risponde. Riprova tra pochi secondi.",
            viewModel.uiState.value.error,
        )
    }

    @Test
    fun `alpha email opens password without sending otp and preserves normal session bootstrap`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        coEvery { repository.emailEligibility("alpha@example.it") } returns EmailEligibilityResponse(true, "password")
        coEvery { repository.signInAlpha("alpha@example.it", "shared-test-password") } returns com.axoo.features.auth.data.dto.SessionBootstrapResponse()
        val model = LoginViewModel(repository)
        val events = mutableListOf<LoginEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.events.collect { events.add(it) } }
        model.onEmailChanged("alpha@example.it")
        model.onTermsAcceptedChanged(true)
        model.onContinueClicked()
        advanceUntilIdle()
        assertEquals(LoginStep.PASSWORD, model.uiState.value.step)
        assertFalse(model.uiState.value.canResend)
        coVerify(exactly = 0) { repository.sendOtp(any()) }
        model.onPasswordChanged("shared-test-password")
        model.onContinueClicked()
        advanceUntilIdle()
        assertEquals("", model.uiState.value.password)
        assertEquals(1, events.filterIsInstance<LoginEvent.LoginSucceeded>().size)
        coVerify(exactly = 1) { repository.signInAlpha("alpha@example.it", "shared-test-password") }
    }

    @Test
    fun `official account uses its own password without sending otp or using alpha login`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        coEvery { repository.emailEligibility("aup@account.example") } returns EmailEligibilityResponse(true, "account_password")
        coEvery { repository.signInWithPassword("aup@account.example", "test-only-password") } returns com.axoo.features.auth.data.dto.SessionBootstrapResponse()
        val model = LoginViewModel(repository)
        model.onEmailChanged("aup@account.example")
        model.onTermsAcceptedChanged(true)
        model.onContinueClicked()
        advanceUntilIdle()
        assertEquals(LoginStep.PASSWORD, model.uiState.value.step)
        assertFalse(model.uiState.value.canResend)
        model.onPasswordChanged("test-only-password")
        model.onContinueClicked()
        advanceUntilIdle()
        assertEquals("", model.uiState.value.password)
        coVerify(exactly = 1) { repository.signInWithPassword("aup@account.example", "test-only-password") }
        coVerify(exactly = 0) { repository.sendOtp(any()) }
        coVerify(exactly = 0) { repository.signInAlpha(any(), any()) }
    }

    @Test
    fun `back from alpha clears password and returns to email`() = runTest {
        val repository = mockk<AuthRepository>(relaxed = true)
        coEvery { repository.emailEligibility("alpha@example.it") } returns EmailEligibilityResponse(true, "password")
        val model = LoginViewModel(repository)
        model.onEmailChanged("alpha@example.it")
        model.onTermsAcceptedChanged(true)
        model.onContinueClicked()
        advanceUntilIdle()
        model.onPasswordChanged("shared-test-password")
        model.onBackClicked()
        assertEquals(LoginStep.EMAIL, model.uiState.value.step)
        assertEquals("", model.uiState.value.password)
    }
}
