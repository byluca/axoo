/**
 * Identifica le sezioni principali dell'app (es. Home, Profilo).
 * Ogni Tab possiede una propria cronologia di navigazione separata.
 */
package com.axoo.application.navigation.route

enum class Tab {
    Auth,
    Home,
    Create,
    Notifications,
    Profile,
}

// Ci dice quale è la primissima schermata da caricare quando clicchiamo su un Tab vuoto
internal val Tab.rootRoute: Route
    get() = when (this) {
        Tab.Auth -> Route.Intro
        Tab.Home -> Route.Home
        Tab.Create -> Route.PostEditor
        Tab.Notifications -> Route.Notifications
        Tab.Profile -> Route.Profile
    }
