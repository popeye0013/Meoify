package com.meoify.music.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meoify.music.data.model.AppSettings
import com.meoify.music.data.model.AudioQuality
import com.meoify.music.data.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.settingsStore by preferencesDataStore(name = "meoify_settings")

class SettingsStore(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val pureBlack = booleanPreferencesKey("pure_black")
        val quality = stringPreferencesKey("quality")
        val losslessServer = stringPreferencesKey("lossless_server")
        val losslessServerName = stringPreferencesKey("lossless_server_name")
        val historySync = booleanPreferencesKey("history_sync")
        val onboarding = booleanPreferencesKey("onboarding_completed")
        val ytCookies = stringPreferencesKey("yt_cookies")
        val accountName = stringPreferencesKey("yt_account")
        val localProfileName = stringPreferencesKey("local_profile_name")
        val localAvatarPath = stringPreferencesKey("local_avatar_path")
        val libraryGrid = booleanPreferencesKey("library_grid")
        val lyricsPreview = booleanPreferencesKey("lyrics_preview")
        val skippedUpdate = stringPreferencesKey("skipped_update_version")
    }

    val settings: StateFlow<AppSettings> = context.settingsStore.data
        .catch { error -> if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error }
        .map { prefs ->
            AppSettings(
                themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.theme] ?: "") }
                    .getOrDefault(ThemeMode.DARK),
                dynamicColor = prefs[Keys.dynamic] ?: false,
                pureBlack = prefs[Keys.pureBlack] ?: true,
                quality = when (prefs[Keys.quality]) {
                    // Legacy names from the three-option picker.
                    "HIGH" -> AudioQuality.HI_RES
                    "NORMAL" -> AudioQuality.STANDARD
                    "SAVER" -> AudioQuality.SAVER
                    else -> runCatching { AudioQuality.valueOf(prefs[Keys.quality] ?: "") }
                        .getOrDefault(AudioQuality.AUTO)
                },
                losslessServer = prefs[Keys.losslessServer]?.takeIf { it.isNotBlank() },
                losslessServerName = prefs[Keys.losslessServerName]?.takeIf { it.isNotBlank() },
                historySync = prefs[Keys.historySync] ?: true,
                onboardingCompleted = prefs[Keys.onboarding] ?: false,
                ytCookies = prefs[Keys.ytCookies],
                accountName = prefs[Keys.accountName],
                localProfileName = prefs[Keys.localProfileName],
                localAvatarPath = prefs[Keys.localAvatarPath],
                libraryGridView = prefs[Keys.libraryGrid] ?: false,
                lyricsPreview = prefs[Keys.lyricsPreview] ?: true,
                skippedUpdateVersion = prefs[Keys.skippedUpdate],
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    fun setThemeMode(mode: ThemeMode) = scope.launch {
        context.settingsStore.edit { it[Keys.theme] = mode.name }
    }

    fun setDynamicColor(enabled: Boolean) = scope.launch {
        context.settingsStore.edit { it[Keys.dynamic] = enabled }
    }

    fun setPureBlack(enabled: Boolean) = scope.launch {
        context.settingsStore.edit { it[Keys.pureBlack] = enabled }
    }

    fun setQuality(quality: AudioQuality) = scope.launch {
        context.settingsStore.edit { it[Keys.quality] = quality.name }
    }

    fun setLosslessServer(url: String?, name: String? = null) = scope.launch {
        context.settingsStore.edit {
            val clean = url?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
            val cleanName = name?.trim()?.takeIf { it.isNotBlank() }
            if (clean == null) {
                it.remove(Keys.losslessServer)
                it.remove(Keys.losslessServerName)
            } else {
                it[Keys.losslessServer] = clean
                if (cleanName == null) it.remove(Keys.losslessServerName)
                else it[Keys.losslessServerName] = cleanName
            }
        }
    }

    fun setHistorySync(enabled: Boolean) = scope.launch {
        context.settingsStore.edit { it[Keys.historySync] = enabled }
    }

    fun setYtCookies(cookies: String?) = scope.launch {
        context.settingsStore.edit {
            if (cookies == null) it.remove(Keys.ytCookies) else it[Keys.ytCookies] = cookies
        }
    }

    fun setAccountName(name: String?) = scope.launch {
        context.settingsStore.edit {
            if (name == null) it.remove(Keys.accountName) else it[Keys.accountName] = name
        }
    }

    fun logoutYouTube() = scope.launch {
        context.settingsStore.edit {
            it.remove(Keys.ytCookies)
            it.remove(Keys.accountName)
        }
    }

    fun setOnboardingCompleted(completed: Boolean) = scope.launch {
        context.settingsStore.edit { it[Keys.onboarding] = completed }
    }

    fun setLocalProfileName(name: String?) = scope.launch {
        context.settingsStore.edit {
            val clean = name?.trim()?.takeIf { it.isNotBlank() }
            if (clean == null) it.remove(Keys.localProfileName) else it[Keys.localProfileName] = clean
        }
    }

    fun setLocalAvatarPath(path: String?) = scope.launch {
        context.settingsStore.edit {
            if (path == null) it.remove(Keys.localAvatarPath) else it[Keys.localAvatarPath] = path
        }
    }

    /** Remembers whether the Library playlists are shown as a grid. */
    fun setLibraryGridView(enabled: Boolean) = scope.launch {
        context.settingsStore.edit { it[Keys.libraryGrid] = enabled }
    }

    /** Remembers the synced-lyrics preview toggle on Now Playing. */
    fun setLyricsPreview(enabled: Boolean) = scope.launch {
        context.settingsStore.edit { it[Keys.lyricsPreview] = enabled }
    }

    /** Remembers the release version whose home-screen update popup was skipped. */
    fun setSkippedUpdateVersion(version: String?) = scope.launch {
        context.settingsStore.edit {
            if (version == null) it.remove(Keys.skippedUpdate) else it[Keys.skippedUpdate] = version
        }
    }

    suspend fun current(): AppSettings = settings.first()
}
