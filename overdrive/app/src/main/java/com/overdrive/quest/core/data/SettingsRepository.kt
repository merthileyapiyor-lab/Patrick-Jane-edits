package com.overdrive.quest.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.overdrive.quest.core.model.AppProfile
import com.overdrive.quest.core.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

private val Context.overdriveStore: DataStore<Preferences> by preferencesDataStore(name = "overdrive")

class SettingsRepository(context: Context, scope: CoroutineScope) {
    private val store = context.applicationContext.overdriveStore
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    /** Cold flows only emit once DataStore has loaded, unlike the StateFlows below. */
    val settingsFlow: Flow<AppSettings> = store.data.map { decodeSettings(it[KEY_SETTINGS]) }.distinctUntilChanged()
    val profilesFlow: Flow<Map<String, AppProfile>> =
        store.data.map { decodeProfiles(it[KEY_PROFILES]) }.distinctUntilChanged()

    val settings: StateFlow<AppSettings> = settingsFlow.stateIn(scope, SharingStarted.Eagerly, AppSettings())
    val appProfiles: StateFlow<Map<String, AppProfile>> =
        profilesFlow.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    suspend fun loaded(): AppSettings = settingsFlow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val current = decodeSettings(prefs[KEY_SETTINGS])
            prefs[KEY_SETTINGS] = json.encodeToString(AppSettings.serializer(), transform(current))
        }
    }

    suspend fun putAppProfile(profile: AppProfile) = editProfiles { it + (profile.packageName to profile) }

    suspend fun removeAppProfile(packageName: String) = editProfiles { it - packageName }

    private suspend fun editProfiles(transform: (Map<String, AppProfile>) -> Map<String, AppProfile>) {
        store.edit { prefs ->
            val next = transform(decodeProfiles(prefs[KEY_PROFILES]))
            prefs[KEY_PROFILES] = json.encodeToString(profileListSerializer, next.values.toList())
        }
    }

    private fun decodeSettings(raw: String?): AppSettings =
        raw?.let { runCatching { json.decodeFromString(AppSettings.serializer(), it) }.getOrNull() } ?: AppSettings()

    private fun decodeProfiles(raw: String?): Map<String, AppProfile> =
        raw?.let { s ->
            runCatching { json.decodeFromString(profileListSerializer, s) }.getOrNull()
                ?.associateBy { it.packageName }
        } ?: emptyMap()

    private companion object {
        val KEY_SETTINGS = stringPreferencesKey("settings_json")
        val KEY_PROFILES = stringPreferencesKey("app_profiles_json")
        val profileListSerializer = kotlinx.serialization.builtins.ListSerializer(AppProfile.serializer())
    }
}
