package com.axoo.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow

internal class MemoryPreferencesStore : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())
    var failWrites = false

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        if (failWrites) throw IOException("storage unavailable")
        return transform(data.value).also { data.value = it }
    }
}
