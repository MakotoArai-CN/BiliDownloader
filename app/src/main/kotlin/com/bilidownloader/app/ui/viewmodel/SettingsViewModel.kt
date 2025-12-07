package com.bilidownloader.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilidownloader.app.data.repository.SettingsRepository
import com.bilidownloader.app.data.repository.ThemeMode
import com.bilidownloader.app.data.repository.WebViewUA
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val repository = SettingsRepository()

    val settings: StateFlow<Settings> = combine(
        repository.getDownloadPath(),
        repository.getCookie(),
        repository.getAgreementAccepted(),
        repository.getThemeMode(),
        repository.getWebViewUA(),
        repository.getFilenameFormat(),
        repository.getMaxConcurrentDownloads(),
        repository.getNetworkWarningEnabled(),
        repository.getBackgroundDownloadEnabled(),
        repository.getDownloadRecordCheckEnabled(),
        repository.getExtraContentEnabled()
    ) { values ->
        Settings(
            downloadPath = values[0] as String,
            cookie = values[1] as String,
            agreementAccepted = values[2] as Boolean,
            themeMode = values[3] as ThemeMode,
            webViewUA = values[4] as WebViewUA,
            filenameFormat = values[5] as String,
            maxConcurrentDownloads = values[6] as Int,
            networkWarningEnabled = values[7] as Boolean,
            backgroundDownloadEnabled = values[8] as Boolean,
            downloadRecordCheckEnabled = values[9] as Boolean,
            extraContentEnabled = values[10] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = Settings()
    )

    fun updateDownloadPath(path: String) {
        viewModelScope.launch {
            repository.setDownloadPath(path)
        }
    }

    fun updateCookie(cookie: String) {
        viewModelScope.launch {
            repository.setCookie(cookie)
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun updateWebViewUA(ua: WebViewUA) {
        viewModelScope.launch {
            repository.setWebViewUA(ua)
        }
    }

    fun updateFilenameFormat(format: String) {
        viewModelScope.launch {
            repository.setFilenameFormat(format)
        }
    }

    fun updateMaxConcurrentDownloads(max: Int) {
        viewModelScope.launch {
            repository.setMaxConcurrentDownloads(max)
        }
    }

    fun updateNetworkWarningEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setNetworkWarningEnabled(enabled)
        }
    }

    fun updateBackgroundDownloadEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBackgroundDownloadEnabled(enabled)
        }
    }

    fun updateDownloadRecordCheckEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDownloadRecordCheckEnabled(enabled)
        }
    }

    fun updateExtraContentEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setExtraContentEnabled(enabled)
        }
    }
}

data class Settings(
    val downloadPath: String = "/storage/emulated/0/Download/BiliDown",
    val cookie: String = "",
    val agreementAccepted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val webViewUA: WebViewUA = WebViewUA.PC,
    val filenameFormat: String = SettingsRepository.DEFAULT_FILENAME_FORMAT,
    val maxConcurrentDownloads: Int = 2,
    val networkWarningEnabled: Boolean = true,
    val backgroundDownloadEnabled: Boolean = true,
    val downloadRecordCheckEnabled: Boolean = true,
    val extraContentEnabled: Boolean = false
)