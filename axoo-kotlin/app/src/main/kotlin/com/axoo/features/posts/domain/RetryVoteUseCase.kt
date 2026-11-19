package com.axoo.features.posts.domain

import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class RetryVoteUseCase @Inject constructor() {
    suspend operator fun <T> invoke(block: suspend () -> T): T? {
        repeat(3) { attempt ->
            val result = runCatching { block() }
            result.getOrNull()?.let { return it }
            val error = result.exceptionOrNull()
            if (error is CancellationException) throw error
            if (isPermanentClientError(error)) return null
            delay(RETRY_DELAYS_MS[attempt])
        }
        return null
    }

    companion object {
        val RETRY_DELAYS_MS = longArrayOf(500, 1_500, 3_000, 7_000, 15_000, 30_000)
    }
}
