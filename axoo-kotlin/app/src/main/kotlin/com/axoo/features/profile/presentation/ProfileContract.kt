package com.axoo.features.profile.presentation

import com.axoo.application.navigation.route.Tab
import com.axoo.features.profile.domain.User

data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
)

sealed interface ProfileEvent {
    data object MenuTapped : ProfileEvent
    data object AvatarTapped : ProfileEvent
    data object NicknameTapped : ProfileEvent
    data object BioTapped : ProfileEvent
    data object MyPostsTapped : ProfileEvent
    data class BottomTabSelected(val tab: Tab) : ProfileEvent
}
