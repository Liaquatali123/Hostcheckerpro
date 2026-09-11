package com.hostchecker.pro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hostchecker.pro.data.prefs.SettingsDataStore
import com.hostchecker.pro.data.prefs.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsDataStore.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    fun updateThreads(threads: Int) {
        viewModelScope.launch {
            settingsDataStore.updateThreads(threads)
        }
    }

    fun updateTimeout(timeout: Int) {
        viewModelScope.launch {
            settingsDataStore.updateTimeout(timeout)
        }
    }

    fun updateRetryFailed(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateRetryFailed(enabled)
        }
    }

    fun updateStealthMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateStealthMode(enabled)
        }
    }

    fun updateJitter(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateJitter(enabled)
        }
    }

    fun updateExportFormat(format: String) {
        viewModelScope.launch {
            settingsDataStore.updateExportFormat(format)
        }
    }

    fun updateNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateNotifications(enabled)
        }
    }

    fun updateShowFailedScans(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateShowFailedScans(enabled)
        }
    }

    class Factory(
        private val settingsDataStore: SettingsDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsDataStore) as T
        }
    }
}
