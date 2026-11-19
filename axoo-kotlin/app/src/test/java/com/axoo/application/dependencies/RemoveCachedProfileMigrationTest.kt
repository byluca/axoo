package com.axoo.application.dependencies

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoveCachedProfileMigrationTest {
    @Test
    fun `upgrade removes the old profile and preserves other preferences`() = runTest {
        val profile = stringPreferencesKey("cached_profile")
        val session = stringPreferencesKey("supabase_access_token")
        val push = stringPreferencesKey("push_installation_id")
        val saved = preferencesOf(profile to "old profile", session to "session", push to "installation")

        assertTrue(RemoveCachedProfileMigration.shouldMigrate(saved))
        val migrated = RemoveCachedProfileMigration.migrate(saved)

        assertEquals(preferencesOf(session to "session", push to "installation").asMap(), migrated.asMap())
        assertFalse(RemoveCachedProfileMigration.shouldMigrate(migrated))
        assertFalse(RemoveCachedProfileMigration.shouldMigrate(emptyPreferences()))
    }
}
