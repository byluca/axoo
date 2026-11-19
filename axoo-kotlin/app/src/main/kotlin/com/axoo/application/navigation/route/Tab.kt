/**
 * Identifica le sezioni principali dell'app (es. Home, Profilo).
 * Ogni Tab possiede una propria cronologia di navigazione separata.
 */
package com.axoo.application.navigation.route

enum class Tab {
    Auth,
    Home,
    Events,
    Create,
    Notifications,
    Profile,
}

// Ci dice quale è la primissima schermata da caricare quando clicchiamo su un Tab vuoto
internal val Tab.rootRoute: Route
    get() = when (this) {
        Tab.Auth -> Route.Intro
        Tab.Home -> Route.Home
        Tab.Events -> Route.Events
        Tab.Create -> Route.PostEditor
        Tab.Notifications -> Route.Notifications
        Tab.Profile -> Route.Profile
    }

// Traduce un tab della UI condivisa (BottomTab) nel nostro concetto logico di navigazione (Tab)
internal fun BottomTab.toTab(): Tab = when (this) {
    BottomTab.HOME -> Tab.Home
    BottomTab.EVENTS -> Tab.Events
    BottomTab.CREATE -> Tab.Create
    BottomTab.NOTIFICATIONS -> Tab.Notifications
    BottomTab.PROFILE -> Tab.Profile
}
