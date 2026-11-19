package com.axoo.features.posts.domain

data class PostAuthor(
    val id: String,
    val nickname: String,
    val universityName: String? = null,
    val avatarConfig: Map<String, Any>? = null,
    val isModerator: Boolean = false,
)

interface PostAccount {
    fun cachedAuthor(): PostAuthor?
    suspend fun loadAuthor(): PostAuthor
    suspend fun blockUser(userId: String)
}
