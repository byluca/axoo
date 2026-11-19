package com.axoo.features.profile.data

import com.axoo.core.session.SupabaseSessionStore
import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.engine.emoji.text.toApiEntityOffsets
import com.axoo.engine.emoji.text.toUiEntityOffsets
import com.axoo.features.posts.data.PostRepository
import com.axoo.features.moderation.data.ReportRequest
import com.axoo.features.moderation.data.ReportsApi
import com.axoo.features.profile.data.dto.DeleteAccountRequest
import com.axoo.features.profile.data.dto.UpdateAvatarRequest
import com.axoo.features.profile.data.dto.UpdateNicknameRequest
import com.axoo.features.profile.data.dto.UpdateBioRequest
import com.axoo.features.profile.domain.PublicProfile
import com.axoo.features.profile.domain.User
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ProfileRepository @Inject constructor(
    private val reportsApi: ReportsApi,
    private val api: ProfileApi,
    private val postRepository: PostRepository,
    private val sessionStore: SupabaseSessionStore,
) {
    private data class CachedProfile(val user: User, val sessionGeneration: Long)
    @Volatile private var cached: CachedProfile? = null
    private val cacheLock = Mutex()
    private var cacheGeneration = 0L

    suspend fun getMe(): User {
        val session = profileSession()
        val user = api.getMe().withUiBioEntities()
        cacheUser(user, session)
        return user
    }

    fun getCachedUser(): User? = cached?.takeIf { it.sessionGeneration == sessionStore.generation }?.user

    suspend fun getUniversityId(): String = (getCachedUser() ?: getMe()).universityId

    val blockedUserIds get() = postRepository.blockedAuthorIds

    suspend fun isOwnProfile(userId: String): Boolean = sessionStore.currentUserId() == userId

    suspend fun getPublicProfile(userId: String): PublicProfile {
        check(userId !in blockedUserIds.value) { "Profilo non disponibile" }
        val session = profileSession()
        val profile = api.getPublicProfile(userId)
        cacheLock.withLock {
            currentCoroutineContext().ensureActive()
            if (session.generation != cacheGeneration || session.sessionGeneration != sessionStore.generation) {
                throw CancellationException("Sessione terminata")
            }
            check(userId !in blockedUserIds.value) { "Profilo non disponibile" }
        }
        return profile.copy(bioEntities = profile.bioEntities.toUiEntityOffsets(profile.bio))
    }

    suspend fun updateProfilePrivacy(isPublic: Boolean): User {
        val session = profileSession()
        val user = api.updateProfilePrivacy(mapOf("is_profile_public" to isPublic)).withUiBioEntities()
        cacheUser(user, session)
        return user
    }

    suspend fun updateBio(bio: String, entities: List<MessageEntity> = emptyList()): User {
        val session = profileSession()
        val user = api.updateBio(UpdateBioRequest(bio, entities.toApiEntityOffsets(bio))).withUiBioEntities()
        cacheUser(user, session)
        return user
    }

    suspend fun updateAvatar(config: Map<String, Any>): User {
        val session = profileSession()
        api.updateAvatar(UpdateAvatarRequest(config))
        val user = api.getMe().withUiBioEntities()
        cacheUser(user, session)
        return user
    }

    suspend fun isNicknameAvailable(nickname: String): Boolean =
        api.getNicknameAvailability(nickname).available

    suspend fun updateNickname(nickname: String): User {
        val session = profileSession()
        val user = api.updateNickname(UpdateNicknameRequest(nickname)).withUiBioEntities()
        cacheUser(user, session)
        return user
    }

    suspend fun blockUser(userId: String) {
        api.blockUser(userId)
        postRepository.setAuthorBlocked(userId, true)
    }

    suspend fun blockedUsers(): List<User> = api.getBlockedUsers().users

    suspend fun reportProfile(userId: String, reason: String) =
        reportsApi.report(ReportRequest(targetType = "user", targetId = userId, reason = reason))

    suspend fun unblockUser(userId: String) {
        api.unblockUser(userId)
        postRepository.setAuthorBlocked(userId, false)
    }

    internal suspend fun requestAccountDeletion() = api.deleteAccount(DeleteAccountRequest())

    suspend fun deleteAccount() {
        requestAccountDeletion()
        clearCache()
    }

    suspend fun clearCache() {
        cacheLock.withLock {
            cacheGeneration++
            cached = null
            postRepository.clearLocalRestrictions()
        }
    }

    private data class ProfileSession(val userId: String, val generation: Long, val sessionGeneration: Long)

    private suspend fun profileSession(): ProfileSession = cacheLock.withLock {
        val sessionGeneration = sessionStore.generation
        val userId = sessionStore.currentUserId()
            ?: throw CancellationException("Sessione terminata")
        ProfileSession(userId, cacheGeneration, sessionGeneration)
    }

    private suspend fun cacheUser(user: User, session: ProfileSession) = cacheLock.withLock {
        currentCoroutineContext().ensureActive()
        if (session.generation != cacheGeneration || session.sessionGeneration != sessionStore.generation ||
            user.id != session.userId ||
            sessionStore.currentUserId() != session.userId
        ) {
            throw CancellationException("Risposta di una sessione precedente")
        }
        cached = CachedProfile(user, session.sessionGeneration)
    }
}

private fun User.withUiBioEntities(): User = copy(bioEntities = bioEntities.toUiEntityOffsets(bio))
