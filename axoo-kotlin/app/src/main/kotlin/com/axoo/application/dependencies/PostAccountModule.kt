package com.axoo.application.dependencies

import com.axoo.features.posts.domain.PostAccount
import com.axoo.features.posts.domain.PostAuthor
import com.axoo.features.profile.data.ProfileRepository
import com.axoo.features.profile.domain.User
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object PostAccountModule {
    @Provides
    fun providePostAccount(profiles: ProfileRepository): PostAccount = object : PostAccount {
        override fun cachedAuthor() = profiles.getCachedUser()?.toPostAuthor()
        override suspend fun loadAuthor() = profiles.getMe().toPostAuthor()
        override suspend fun blockUser(userId: String) = profiles.blockUser(userId)
    }
}

private fun User.toPostAuthor() = PostAuthor(id, nickname, universityName, avatarConfig, isModerator)
