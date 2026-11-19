package com.axoo.features.posts.domain

import retrofit2.HttpException

internal fun isPermanentClientError(error: Throwable?): Boolean {
    val code = (error as? HttpException)?.code() ?: return false
    return code in 400..499 && code != 408 && code != 425 && code != 429
}
