package com.hostchecker.pro.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "host_checker_settings")

data class UserSettings(
    val defaultThreads: Int = 6,
    val defaultTimeoutSeconds: Int = 10,
    val retryFailed: Boolean = false,
    val stealthMode: Boolean = false,
    val jitterEnabled: Boolean = false,
    val defaultExportFormat: String = "CSV", // CSV, TXT, JSON
    val notificationsEnabled: Boolean = true,
    val showFailedScans: Boolean = true
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val THREADS = intPreferencesKey("default_threads")
        val TIMEOUT = intPreferencesKey("default_timeout")
        val RETRY_FAILED = booleanPreferencesKey("retry_failed")
        val STEALTH_MODE = booleanPreferencesKey("stealth_mode")
        val JITTER = booleanPreferencesKey("jitter_enabled")
        val EXPORT_FORMAT = stringPreferencesKey("default_export_format")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val SHOW_FAILED = booleanPreferencesKey("show_failed_scans")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            defaultThreads = prefs[Keys.THREADS] ?: 6,
            defaultTimeoutSeconds = prefs[Keys.TIMEOUT] ?: 10,
            retryFailed = prefs[Keys.RETRY_FAILED] ?: false,
            stealthMode = prefs[Keys.STEALTH_MODE] ?: false,
            jitterEnabled = prefs[Keys.JITTER] ?: false,
            defaultExportFormat = prefs[Keys.EXPORT_FORMAT] ?: "CSV",
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            showFailedScans = prefs[Keys.SHOW_FAILED] ?: true
        )
    }

    suspend fun updateThreads(threads: Int) {
        context.dataStore.edit { it[Keys.THREADS] = threads.coerceIn(1, 64) }
    }

    suspend fun updateTimeout(timeout: Int) {
        context.dataStore.edit { it[Keys.TIMEOUT] = timeout.coerceIn(1, 60) }
    }

    suspend fun updateRetryFailed(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RETRY_FAILED] = enabled }
    }

    suspend fun updateStealthMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.STEALTH_MODE] = enabled }
    }

    suspend fun updateJitter(enabled: Boolean) {
        context.dataStore.edit { it[Keys.JITTER] = enabled }
    }

    suspend fun updateExportFormat(format: String) {
        context.dataStore.edit { it[Keys.EXPORT_FORMAT] = format }
    }

    suspend fun updateNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun updateShowFailedScans(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_FAILED] = enabled }
    }
}
