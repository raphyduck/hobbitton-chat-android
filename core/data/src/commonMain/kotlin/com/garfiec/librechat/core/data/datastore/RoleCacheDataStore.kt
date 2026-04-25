package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class RoleCacheDataStore(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {

    suspend fun save(role: UserRolePermissions) {
        try {
            val serialized = json.encodeToString(UserRolePermissions.serializer(), role)
            dataStore.edit { prefs -> prefs[KEY_ROLE] = serialized }
        } catch (e: Exception) {
            Logger.w(e) { "Failed to cache user role permissions" }
        }
    }

    suspend fun load(): UserRolePermissions? {
        return try {
            val prefs = dataStore.data.first()
            val serialized = prefs[KEY_ROLE] ?: return null
            json.decodeFromString(UserRolePermissions.serializer(), serialized)
        } catch (e: Exception) {
            Logger.w(e) { "Failed to load cached user role permissions" }
            null
        }
    }

    suspend fun clear() {
        try {
            dataStore.edit { prefs -> prefs.remove(KEY_ROLE) }
        } catch (e: Exception) {
            Logger.w(e) { "Failed to clear cached user role permissions" }
        }
    }

    private companion object {
        val KEY_ROLE = stringPreferencesKey("cached_user_role_permissions")
    }
}
