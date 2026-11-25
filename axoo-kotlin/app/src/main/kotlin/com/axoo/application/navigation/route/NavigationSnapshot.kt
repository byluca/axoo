/**
 * Modello dati per il salvataggio dello stato di navigazione.
 * Permette di ripristinare la cronologia e i tab se l'app viene chiusa in background dal sistema.
 */
package com.axoo.application.navigation.route

import java.net.URLDecoder
import java.net.URLEncoder

data class PersistedRoute(
    val type: String,
    val args: Map<String, String> = emptyMap(),
)

data class NavigationSnapshot(
    val currentTab: Tab,
    val stacks: Map<Tab, List<PersistedRoute>>,
) {
    fun encode(): String {
        return buildString {
            append("current=")
            append(currentTab.name)
            append('\n')
            Tab.entries.forEach { tab ->
                append(tab.name)
                append('=')
                append(stacks[tab].orEmpty().joinToString(",") { it.encode() })
                append('\n')
            }
        }
    }

    companion object {
        fun decode(raw: String?): NavigationSnapshot? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val lines = raw.lineSequence()
                    .filter { it.isNotBlank() && it.contains("=") }
                    .associate { line ->
                        line.substringBefore("=") to line.substringAfter("=")
                    }
                val currentName = lines.getValue("current")
                val current = Tab.entries.firstOrNull { it.name == currentName } ?: Tab.Home
                val stacks = Tab.entries.associateWith { tab ->
                    lines[tab.name]
                        ?.takeIf { it.isNotBlank() }
                        ?.split(",")
                        ?.mapNotNull(::decodePersistedRoute)
                        ?.ifEmpty { listOf(tab.rootRoute.toPersistedRoute()) }
                        ?: listOf(tab.rootRoute.toPersistedRoute())
                }
                NavigationSnapshot(current, stacks)
            }.getOrNull()
        }
    }
}

fun Route.toPersistedRoute(): PersistedRoute = when (this) {
    Route.Intro -> PersistedRoute("intro")
    Route.Login -> PersistedRoute("login")
    Route.Notifications -> PersistedRoute("notifications")
    Route.Home -> PersistedRoute("home")
    Route.PostEditor -> PersistedRoute("create_post")
    Route.MyPosts -> PersistedRoute("my_posts")
    Route.Profile -> PersistedRoute("profile")
    Route.ProfileSettings -> PersistedRoute("profile_settings")
    Route.NicknameEditor -> PersistedRoute("nickname_editor")
    Route.BioEditor -> PersistedRoute("bio_editor")
    is Route.UserProfile -> PersistedRoute("user_profile", mapOf("userId" to userId))
    Route.BlockedUsers -> PersistedRoute("blocked_users")
    Route.Moderation -> PersistedRoute("moderation")
    is Route.PostDetail -> PersistedRoute("post_detail", buildMap { put("postId", postId); commentId?.let { put("commentId", it) } })
    is Route.EditPost -> PersistedRoute("edit_post", mapOf("postId" to postId))
}

fun PersistedRoute.toRoute(): Route? = when (type) {
    "intro" -> Route.Intro
    "login" -> Route.Login
    "notifications" -> Route.Notifications
    "home" -> Route.Home
    "create_post" -> Route.PostEditor
    "my_posts" -> Route.MyPosts
    "profile" -> Route.Profile
    "profile_settings" -> Route.ProfileSettings
    "nickname_editor" -> Route.NicknameEditor
    "bio_editor" -> Route.BioEditor
    "user_profile" -> args["userId"]?.takeIf { it.isNotBlank() }?.let(Route::UserProfile)
    "blocked_users" -> Route.BlockedUsers
    "moderation" -> Route.Moderation
    "post_detail" -> args["postId"]?.let { Route.PostDetail(it, commentId = args["commentId"]) }
    "edit_post" -> args["postId"]?.let(Route::EditPost)
    else -> null
}

private fun PersistedRoute.encode(): String {
    val encodedArgs = args.entries.joinToString("&") { (key, value) ->
        "${key.urlEncode()}:${value.urlEncode()}"
    }
    return if (encodedArgs.isBlank()) type.urlEncode() else "${type.urlEncode()}?$encodedArgs"
}

private fun decodePersistedRoute(raw: String): PersistedRoute? {
    val type = raw.substringBefore("?").urlDecode()
    val argsRaw = raw.substringAfter("?", missingDelimiterValue = "")
    val args = if (argsRaw.isBlank()) {
        emptyMap()
    } else {
        argsRaw.split("&").mapNotNull { pair ->
            val key = pair.substringBefore(":", missingDelimiterValue = "")
            val value = pair.substringAfter(":", missingDelimiterValue = "")
            if (key.isBlank()) null else key.urlDecode() to value.urlDecode()
        }.toMap()
    }
    return PersistedRoute(type, args)
}

private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

private fun String.urlDecode(): String = URLDecoder.decode(this, Charsets.UTF_8.name())
