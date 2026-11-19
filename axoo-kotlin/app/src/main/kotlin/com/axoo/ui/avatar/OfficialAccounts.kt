package com.axoo.ui.avatar

import android.graphics.drawable.BitmapDrawable
import android.widget.TextView
import com.axoo.engine.emoji.image.CoilBitmapLoader
import com.axoo.ui.theme.dp

private const val OFFICIAL_AVATARS = "https://cyrfznxopjeeidcmfgze.supabase.co/storage/v1/object/public/memes/avatars/official/v1/"

fun officialAvatarUrl(config: Map<String, Any>?): String? = when (config?.get("official_account")) {
    "axoo" -> OFFICIAL_AVATARS + "axoo-classic.png"
    "aup" -> OFFICIAL_AVATARS + "aup.jpg"
    else -> null
}

fun isVerifiedAvatar(config: Map<String, Any>?): Boolean = officialAvatarUrl(config) != null

internal object VerifiedBadge {
    fun bind(name: TextView, verified: Boolean) {
        name.setCompoundDrawablesRelative(null, null, null, null)
        name.contentDescription = if (verified) "${name.text}, account verificato" else null
        if (!verified) return
        val description = name.contentDescription
        val context = name.context
        val data = CoilBitmapLoader.assetData("icons/verification.svg")
        val size = name.dp(20)
        CoilBitmapLoader.enqueue(context, data, size) {
            if (name.contentDescription == description) {
                CoilBitmapLoader.peek(context, data, size)?.let { bitmap ->
                    val drawable = BitmapDrawable(context.resources, bitmap).apply { setBounds(0, 0, size, size) }
                    name.compoundDrawablePadding = name.dp(4)
                    name.setCompoundDrawablesRelative(null, null, drawable, null)
                }
            }
        }
    }
}
