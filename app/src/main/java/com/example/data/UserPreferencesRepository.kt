package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Modern Jetpack DataStore repository to persistently store user configurations
 * such as Auto-purge duration, Display theme, display name, power saver, and background sync.
 */
class UserPreferencesRepository(private val context: Context) {

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_AUTO_PURGE_DURATION = stringPreferencesKey("auto_purge_duration")
        val KEY_MY_NICKNAME = stringPreferencesKey("my_nickname")
        val KEY_POWER_SAVER = booleanPreferencesKey("power_saver_enabled")
        val KEY_AUTO_ARCHIVE = booleanPreferencesKey("auto_archive_enabled")
        val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val KEY_HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val KEY_DIAGNOSTIC_OVERLAY = booleanPreferencesKey("diagnostic_overlay_visible")
        val KEY_BACKGROUND_SYNC_ENABLED = booleanPreferencesKey("background_sync_enabled")
        val KEY_LAST_BACKGROUND_SYNC = stringPreferencesKey("last_background_sync_timestamp")
    }

    val themeModeFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_THEME_MODE] ?: migrateLegacyTheme()
        }

    val appLanguageFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_APP_LANGUAGE] ?: "SYSTEM"
        }

    val autoPurgeDurationFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_AUTO_PURGE_DURATION] ?: migrateLegacyAutoPurge()
        }

    val myNicknameFlow: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_MY_NICKNAME] ?: migrateLegacyNickname()
        }

    val powerSaverFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_POWER_SAVER] ?: false
        }

    val autoArchiveFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_AUTO_ARCHIVE] ?: false
        }

    val soundEnabledFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_SOUND_ENABLED] ?: true
        }

    val hapticEnabledFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_HAPTIC_ENABLED] ?: true
        }

    val diagnosticOverlayFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_DIAGNOSTIC_OVERLAY] ?: true
        }

    val backgroundSyncEnabledFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_BACKGROUND_SYNC_ENABLED] ?: true
        }

    val lastBackgroundSyncFlow: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_LAST_BACKGROUND_SYNC]
        }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode
        }
    }

    suspend fun setAppLanguage(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_APP_LANGUAGE] = languageCode
        }
    }

    suspend fun setAutoPurgeDuration(duration: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_PURGE_DURATION] = duration
        }
    }

    suspend fun setMyNickname(nickname: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MY_NICKNAME] = nickname
        }
    }

    suspend fun setPowerSaverEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_POWER_SAVER] = enabled
        }
    }

    suspend fun setAutoArchiveEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_ARCHIVE] = enabled
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SOUND_ENABLED] = enabled
        }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_HAPTIC_ENABLED] = enabled
        }
    }

    suspend fun setDiagnosticOverlayVisible(visible: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DIAGNOSTIC_OVERLAY] = visible
        }
    }

    suspend fun setBackgroundSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BACKGROUND_SYNC_ENABLED] = enabled
        }
    }

    suspend fun updateLastBackgroundSync(timestampStr: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_BACKGROUND_SYNC] = timestampStr
        }
    }

    // Seamless migration helpers from legacy SharedPreferences
    private fun migrateLegacyTheme(): String {
        val legacyPrefs = context.getSharedPreferences("p2p_prefs", Context.MODE_PRIVATE)
        return legacyPrefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
    }

    private fun migrateLegacyAutoPurge(): String {
        val legacyPrefs = context.getSharedPreferences("p2p_prefs", Context.MODE_PRIVATE)
        return legacyPrefs.getString("auto_purge_duration", "OFF") ?: "OFF"
    }

    private fun migrateLegacyNickname(): String? {
        val legacyPrefs = context.getSharedPreferences("p2p_prefs", Context.MODE_PRIVATE)
        return legacyPrefs.getString("my_nickname", null)
    }
}
