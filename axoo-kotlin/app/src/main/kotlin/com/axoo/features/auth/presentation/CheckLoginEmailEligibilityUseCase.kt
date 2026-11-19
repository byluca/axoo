package com.axoo.features.auth.presentation

import com.axoo.features.auth.data.AuthRepository
import com.axoo.features.auth.data.dto.EmailEligibilityResponse
import com.axoo.features.auth.domain.isValidLoginEmailFormat
import javax.inject.Inject

class CheckLoginEmailEligibilityUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String): EmailEligibilityResponse {
        if (!isValidLoginEmailFormat(email)) return EmailEligibilityResponse(false)
        return authRepository.emailEligibility(email.trim().lowercase())
    }
}
