package com.axoo.features.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axoo.features.profile.data.ProfileRepository
import com.axoo.features.profile.domain.User
import com.axoo.features.profile.domain.trimBioPayload
import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.ui.state.UiState
import com.squareup.moshi.Moshi
import retrofit2.HttpException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class NicknameStatus { Current, Checking, Available, Invalid, Saving, Saved }

const val MAX_BIO_LENGTH = 200

data class BioState(
    val value: String = "",
    val original: String? = null,
    val entities: List<MessageEntity> = emptyList(),
    val originalEntities: List<MessageEntity> = emptyList(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val length: Int get() = value.codePointCount(0, value.length)
    val canSave: Boolean get() {
        val payload = trimBioPayload(value, entities)
        return original != null && (payload.text != original || payload.entities != originalEntities) &&
            length <= MAX_BIO_LENGTH && !saving
    }
}

data class NicknameState(
    val value: String = "",
    val currentNickname: String? = null,
    val status: NicknameStatus = NicknameStatus.Current,
    val message: String = "Questo è il tuo nickname attuale",
) {
    val canSave: Boolean get() = currentNickname != null && value != currentNickname && status == NicknameStatus.Available
}

data class BlockedUsersState(
    val users: UiState<List<User>> = UiState.Loading,
    val unblockingUserId: String? = null,
    val operationFailed: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(private val repo: ProfileRepository) : ViewModel() {
    private val _me = MutableStateFlow<UiState<User>>(
        repo.getCachedUser()?.let { UiState.Success(it) } ?: UiState.Loading,
    )
    val me: StateFlow<UiState<User>> = _me.asStateFlow()
    private val _pendingProfilePublic = MutableStateFlow<Boolean?>(null)
    val pendingProfilePublic = _pendingProfilePublic.asStateFlow()
    private val _isDeletingAccount = MutableStateFlow(false)
    val isDeletingAccount: StateFlow<Boolean> = _isDeletingAccount.asStateFlow()
    private val _nickname = MutableStateFlow(NicknameState())
    val nickname: StateFlow<NicknameState> = _nickname.asStateFlow()
    private val _blockedUsers = MutableStateFlow(BlockedUsersState())
    val blockedUsers: StateFlow<BlockedUsersState> = _blockedUsers.asStateFlow()
    private val _bio = MutableStateFlow(BioState())
    val bio: StateFlow<BioState> = _bio.asStateFlow()
    private var bioJob: Job? = null
    private var bioEditing = false
    private var refreshJob: Job? = null
    private var nicknameJob: Job? = null
    private var blockedJob: Job? = null
    private var nicknameEditing = false
    private var blockedUsersVisible = false

    init { if (_me.value.getOrNull() == null) refresh() }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val previous = _me.value
            try {
                if (previous !is UiState.Success) _me.value = UiState.Loading
                val user = repo.getMe()
                currentCoroutineContext().ensureActive()
                updateProfile(user)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                currentCoroutineContext().ensureActive()
                if (previous !is UiState.Success) _me.value = UiState.Error(error)
            } finally {
                refreshJob = null
            }
        }
    }

    private fun updateProfile(user: User) {
        _me.value = UiState.Success(user)
        if (bioEditing && _bio.value.original == null) {
            _bio.value = _bio.value.copy(value = user.bio, original = user.bio,
                entities = user.bioEntities, originalEntities = user.bioEntities)
        }
        if (nicknameEditing && _nickname.value.currentNickname == null) {
            val draft = _nickname.value.value.ifEmpty { user.nickname }
            _nickname.value = _nickname.value.copy(currentNickname = user.nickname)
            changeNickname(draft)
        }
    }

    fun setProfilePublic(isPublic: Boolean, onFailure: () -> Unit) {
        if (_pendingProfilePublic.value != null || _me.value.getOrNull() == null) return
        refreshJob?.cancel(); refreshJob = null
        _pendingProfilePublic.value = isPublic
        viewModelScope.launch {
            try {
                updateProfile(repo.updateProfilePrivacy(isPublic))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                refresh()
                onFailure()
            } finally { _pendingProfilePublic.value = null }
        }
    }

    fun updateAvatar(
        config: Map<String, Any>,
        onSuccess: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {},
    ) {
        viewModelScope.launch {
            try {
                val user = repo.updateAvatar(config)
                currentCoroutineContext().ensureActive()
                updateProfile(user)
                onSuccess()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                currentCoroutineContext().ensureActive()
                onFailure(error)
            }
        }
    }

    fun beginNicknameEditing() {
        stopNicknameEditing()
        nicknameEditing = true
        val current = _me.value.getOrNull()?.nickname
        _nickname.value = NicknameState(value = current.orEmpty(), currentNickname = current)
        changeNickname(current.orEmpty())
    }

    suspend fun getPublicProfile(userId: String) = repo.getPublicProfile(userId)

    suspend fun isOwnProfile(userId: String) = repo.isOwnProfile(userId)

    suspend fun blockUser(userId: String) = repo.blockUser(userId)

    suspend fun reportProfile(userId: String, reason: String) = repo.reportProfile(userId, reason)

    fun beginBioEditing() {
        stopBioEditing()
        bioEditing = true
        val user = _me.value.getOrNull()
        val current = user?.bio
        _bio.value = BioState(value = current.orEmpty(), original = current,
            entities = user?.bioEntities.orEmpty(), originalEntities = user?.bioEntities.orEmpty())
        if (current == null) refresh()
    }

    fun changeBio(value: String, entities: List<MessageEntity> = emptyList()) {
        if (!bioEditing || _bio.value.saving) return
        _bio.value = _bio.value.copy(value = value, entities = entities, error = null)
    }

    fun saveBio() {
        val draft = _bio.value
        if (!bioEditing || !draft.canSave) return
        _bio.value = draft.copy(saving = true, error = null)
        bioJob = viewModelScope.launch {
            try {
                val payload = trimBioPayload(draft.value, draft.entities)
                val user = repo.updateBio(payload.text, payload.entities)
                currentCoroutineContext().ensureActive()
                updateProfile(user)
                _bio.value = BioState(value = user.bio, original = user.bio,
                    entities = user.bioEntities, originalEntities = user.bioEntities, saved = true)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                currentCoroutineContext().ensureActive()
                _bio.value = _bio.value.copy(saving = false, error = "Impossibile salvare la bio. Riprova.")
            }
        }
    }

    fun stopBioEditing() {
        bioEditing = false
        bioJob?.cancel()
        bioJob = null
    }

    fun changeNickname(rawValue: String) {
        if (!nicknameEditing || _nickname.value.status == NicknameStatus.Saving) return
        nicknameJob?.cancel()
        val value = rawValue.trim()
        val error = when {
            value.length < 3 -> "Il nickname deve avere almeno 3 caratteri"
            value.length > 24 -> "Il nickname può avere massimo 24 caratteri"
            value.any { it !in 'a'..'z' && it !in '0'..'9' } -> "Usa solo lettere minuscole e numeri"
            else -> null
        }
        _nickname.value = _nickname.value.copy(
            value = value,
            status = when {
                value == _nickname.value.currentNickname -> NicknameStatus.Current
                error != null -> NicknameStatus.Invalid
                else -> NicknameStatus.Checking
            },
            message = when {
                value == _nickname.value.currentNickname -> "Questo è il tuo nickname attuale"
                error != null -> error
                else -> "Verifica disponibilità…"
            },
        )
        if (_nickname.value.status != NicknameStatus.Checking) return
        nicknameJob = viewModelScope.launch {
            delay(400)
            try {
                val available = repo.isNicknameAvailable(value)
                currentCoroutineContext().ensureActive()
                _nickname.value = _nickname.value.copy(
                    status = if (available) NicknameStatus.Available else NicknameStatus.Invalid,
                    message = if (available) "Nickname disponibile" else "Nickname già in uso",
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                currentCoroutineContext().ensureActive()
                _nickname.value = _nickname.value.copy(
                    status = NicknameStatus.Invalid,
                    message = error.nicknameErrorMessage("Verifica non riuscita. Modifica il nickname per riprovare."),
                )
            }
        }
    }

    fun saveNickname() {
        val draft = _nickname.value
        if (!nicknameEditing || !draft.canSave) return
        nicknameJob?.cancel()
        _nickname.value = draft.copy(status = NicknameStatus.Saving, message = "Salvataggio…")
        nicknameJob = viewModelScope.launch {
            try {
                val user = repo.updateNickname(draft.value)
                currentCoroutineContext().ensureActive()
                updateProfile(user)
                _nickname.value = _nickname.value.copy(status = NicknameStatus.Saved, currentNickname = user.nickname)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                currentCoroutineContext().ensureActive()
                _nickname.value = _nickname.value.copy(
                    status = NicknameStatus.Invalid,
                    message = error.nicknameErrorMessage("Impossibile salvare. Riprova."),
                )
            }
        }
    }

    fun stopNicknameEditing() {
        nicknameEditing = false
        nicknameJob?.cancel()
        nicknameJob = null
    }

    fun loadBlockedUsers() {
        blockedUsersVisible = true
        if (blockedJob?.isActive == true) return
        _blockedUsers.value = BlockedUsersState()
        blockedJob = viewModelScope.launch {
            try {
                val users = repo.blockedUsers()
                currentCoroutineContext().ensureActive()
                _blockedUsers.value = BlockedUsersState(users = UiState.Success(users))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                currentCoroutineContext().ensureActive()
                _blockedUsers.value = BlockedUsersState(users = UiState.Error(error))
            }
        }
    }

    fun unblockUser(userId: String) {
        val state = _blockedUsers.value
        val users = state.users.getOrNull() ?: return
        if (!blockedUsersVisible || state.unblockingUserId != null || users.none { it.id == userId }) return
        blockedJob?.cancel()
        _blockedUsers.value = state.copy(unblockingUserId = userId, operationFailed = false)
        blockedJob = viewModelScope.launch {
            try {
                repo.unblockUser(userId)
                currentCoroutineContext().ensureActive()
                _blockedUsers.value = BlockedUsersState(users = UiState.Success(users.filterNot { it.id == userId }))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                currentCoroutineContext().ensureActive()
                _blockedUsers.value = state.copy(operationFailed = true)
            }
        }
    }

    fun stopBlockedUsers() {
        blockedUsersVisible = false
        blockedJob?.cancel()
        blockedJob = null
        _blockedUsers.value = _blockedUsers.value.copy(unblockingUserId = null)
    }

    fun deleteAccount(onSuccess: () -> Unit, onFailure: (Throwable) -> Unit = {}, confirmation: String = "ELIMINA") {
        if (!confirmation.trim().equals("ELIMINA", ignoreCase = true)) {
            onFailure(AccountDeletionConfirmationError())
            return
        }
        if (!_isDeletingAccount.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                repo.deleteAccount()
                onSuccess()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                currentCoroutineContext().ensureActive()
                onFailure(error)
            } finally {
                _isDeletingAccount.value = false
            }
        }
    }
}

class AccountDeletionConfirmationError : IllegalArgumentException()

private fun Exception.nicknameErrorMessage(fallback: String): String {
    val body = (this as? HttpException)?.response()?.errorBody()?.string() ?: return fallback
    val code = runCatching {
        Moshi.Builder().build().adapter(Map::class.java).fromJson(body)?.get("code")
    }.getOrNull()
    return if (code == "prohibited_words") "Il testo contiene parole non consentite. Modificalo e riprova." else fallback
}
