/**
 * Rappresenta le destinazioni possibili dell'app.
 * Ogni classe di questa gerarchia definisce una schermata e i parametri necessari per aprirla.
 */
package com.axoo.application.navigation.route

// APPUNTI (Definizione Rotte):
// 1. Route.kt è il "vocabolario" della navigazione: definisce dove possiamo andare.
// 2. Ogni object/class è una schermata esatta.
// 3. Essendo sealed, il compilatore ti obbliga a gestire ogni singola rotta nei blocchi 'when'.
sealed interface Route {
    data object Intro : Route
    data object Login : Route
    data object MyPosts : Route
    data object Profile : Route
    data object ProfileSettings : Route
    data object NicknameEditor : Route
    data object BioEditor : Route
    data class UserProfile(val userId: String) : Route
    data object BlockedUsers : Route
    data object Moderation : Route
    data object Events : Route
    data class EventDetail(val eventId: String) : Route
    data object Notifications : Route
    data object Home : Route
    data object PostEditor : Route
    data class PostDetail(val postId: String, val initialPost: com.axoo.features.posts.domain.Post? = null, val commentId: String? = null) : Route
    data class EditPost(val postId: String) : Route
}
