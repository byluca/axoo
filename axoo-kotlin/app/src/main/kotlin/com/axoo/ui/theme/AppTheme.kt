package com.axoo.ui.theme

import android.content.Context
import android.content.res.Configuration

/** One local preference; Android supplies the matching values/values-night resources. */
object AppTheme {
    private fun preferences(context: Context) = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    fun isDark(context: Context): Boolean = preferences(context).getBoolean("dark_theme", false)

    fun setDark(context: Context, dark: Boolean): Boolean {
        if (isDark(context) == dark) return false
        preferences(context).edit().putBoolean("dark_theme", dark).apply()
        return true
    }

    fun wrap(context: Context): Context = context.createConfigurationContext(Configuration().apply {
        uiMode = if (isDark(context)) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    })
}
