package com.axoo.engine.emoji.picker

import android.content.Context
import com.axoo.engine.emoji.catalog.Emoji
import com.axoo.engine.emoji.catalog.EmojiData

internal object RecentEmoji {
    private fun preferences(context: Context) = context.getSharedPreferences("emoji-picker", Context.MODE_PRIVATE)
    fun items(context: Context): List<Emoji> = preferences(context).getString("recent", "").orEmpty()
        .split(',').mapNotNull { it.toIntOrNull()?.let(EmojiData.byId::get) }
        .filter { !it.pickerHidden }.distinctBy { it.id }.take(36)

    fun record(context: Context, emoji: Emoji) {
        val ids = (listOf(emoji) + items(context)).distinctBy { it.id }.take(36).joinToString(",") { it.id.toString() }
        preferences(context).edit().putString("recent", ids).apply()
    }
}
