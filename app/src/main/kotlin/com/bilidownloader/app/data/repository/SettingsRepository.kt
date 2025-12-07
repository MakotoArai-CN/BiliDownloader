package com.bilidownloader.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bilidownloader.app.BiliApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2);

    companion object {
        fun fromValue(value: Int): ThemeMode {
            return entries.find { it.value == value } ?: SYSTEM
        }
    }
}

enum class WebViewUA(val value: Int) {
    PC(0),
    MOBILE(1);

    companion object {
        fun fromValue(value: Int): WebViewUA {
            return entries.find { it.value == value } ?: PC
        }
    }
}

class SettingsRepository {
    private val context = BiliApp.instance

    private object Keys {
        val DOWNLOAD_PATH = stringPreferencesKey("download_path")
        val COOKIE = stringPreferencesKey("cookie")
        val AGREEMENT_ACCEPTED = booleanPreferencesKey("agreement_accepted")
        val PERMISSION_REQUESTED = booleanPreferencesKey("permission_requested")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val WEBVIEW_UA = intPreferencesKey("webview_ua")
        val FILENAME_FORMAT = stringPreferencesKey("filename_format")
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val NETWORK_WARNING_ENABLED = booleanPreferencesKey("network_warning_enabled")
        val BACKGROUND_DOWNLOAD_ENABLED = booleanPreferencesKey("background_download_enabled")
        val DOWNLOAD_RECORD_CHECK_ENABLED = booleanPreferencesKey("download_record_check_enabled")
        val EXTRA_CONTENT_ENABLED = booleanPreferencesKey("extra_content_enabled")
    }

    companion object {
        const val DEFAULT_FILENAME_FORMAT = "{title}"
        val FILENAME_TOKENS = listOf(
            "{title}" to "视频标题",
            "{author}" to "UP主/作者",
            "{bvid}" to "BV号",
            "{avid}" to "AV号",
            "{part_title}" to "分P标题",
            "{part_num}" to "分P序号",
            "{quality}" to "清晰度",
            "{date}" to "下载日期"
        )
    }

    fun getDownloadPath(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.DOWNLOAD_PATH] ?: "/storage/emulated/0/Download/BiliDown"
    }

    suspend fun setDownloadPath(path: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DOWNLOAD_PATH] = path
        }
    }

    fun getCookie(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.COOKIE] ?: ""
    }

    suspend fun setCookie(cookie: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.COOKIE] = cookie
        }
    }

    fun getAgreementAccepted(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AGREEMENT_ACCEPTED] ?: false
    }

    suspend fun setAgreementAccepted(accepted: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AGREEMENT_ACCEPTED] = accepted
        }
    }

    fun getPermissionRequested(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.PERMISSION_REQUESTED] ?: false
    }

    suspend fun setPermissionRequested(requested: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PERMISSION_REQUESTED] = requested
        }
    }

    fun getThemeMode(): Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.fromValue(prefs[Keys.THEME_MODE] ?: 0)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.value
        }
    }

    fun getWebViewUA(): Flow<WebViewUA> = context.dataStore.data.map { prefs ->
        WebViewUA.fromValue(prefs[Keys.WEBVIEW_UA] ?: 0)
    }

    suspend fun setWebViewUA(ua: WebViewUA) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WEBVIEW_UA] = ua.value
        }
    }

    fun getFilenameFormat(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.FILENAME_FORMAT] ?: DEFAULT_FILENAME_FORMAT
    }

    suspend fun setFilenameFormat(format: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FILENAME_FORMAT] = format
        }
    }

    fun getMaxConcurrentDownloads(): Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.MAX_CONCURRENT_DOWNLOADS] ?: 2
    }

    suspend fun setMaxConcurrentDownloads(max: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MAX_CONCURRENT_DOWNLOADS] = max
        }
    }

    fun getNetworkWarningEnabled(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.NETWORK_WARNING_ENABLED] ?: true
    }

    suspend fun setNetworkWarningEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NETWORK_WARNING_ENABLED] = enabled
        }
    }

    fun getBackgroundDownloadEnabled(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.BACKGROUND_DOWNLOAD_ENABLED] ?: true
    }

    suspend fun setBackgroundDownloadEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BACKGROUND_DOWNLOAD_ENABLED] = enabled
        }
    }

    fun getDownloadRecordCheckEnabled(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DOWNLOAD_RECORD_CHECK_ENABLED] ?: true
    }

    suspend fun setDownloadRecordCheckEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DOWNLOAD_RECORD_CHECK_ENABLED] = enabled
        }
    }

    fun getExtraContentEnabled(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.EXTRA_CONTENT_ENABLED] ?: false
    }

    suspend fun setExtraContentEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EXTRA_CONTENT_ENABLED] = enabled
        }
    }
}