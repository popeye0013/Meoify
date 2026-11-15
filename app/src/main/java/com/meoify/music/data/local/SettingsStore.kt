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
        val blur = booleanPreferencesKey("blur_player")
        val animatedCanvas = booleanPreferencesKey("animated_canvas")
        val canvasOverCellular = booleanPreferencesKey("canvas_over_cellular")
        val historySync = booleanPreferencesKey("history_sync")
        val onboarding = booleanPreferencesKey("onboarding_completed")
        val ytCookies = stringPreferencesKey("yt_cookies")
        val accountName = stringPreferencesKey("yt_account")
        val localProfileName = stringPreferencesKey("local_profile_name")
        val localAvatarPath = stringPreferencesKey("local_avatar_path")
    }

    val settings: StateFlow<AppSettings> = context.settingsStore.data
        .catch { error -> if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error }
        .map { prefs ->
            AppSettings(
                themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.theme] ?: "") }
                    .getOrDefault(ThemeMode.DARK),
                dynamicColor = prefs[Keys.dynamic] ?: false,
                pureBlack = prefs[Keys.pureBlack] ?: true,
                quality = runCatching { AudioQuality.valueOf(prefs[Keys.quality] ?: "") }
                    .getOrDefault(AudioQuality.HIGH),
                blurPlayerBackground = prefs[Keys.blur] ?: true,
                animatedCanvas = prefs[Keys.animatedCanvas] ?: true,
                canvasOverCellular = prefs[Keys.canvasOverCellular] ?: false,
                historySync = prefs[Keys.historySync] ?: true,
                onboardingCompleted = prefs[Keys.onboarding] ?: false,
                ytCookies = prefs[Keys.ytCookies],
                accountName = prefs[Keys.accountName],
                localProfileName = prefs[Keys.localProfileName],
                localAvatarPath = prefs[Keys.localAvatarPath],
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

    fun setBlur(enabled: Boolean) = scope.launch {
        context.settingsStore.edit { it[Keys.blur] = enabled }
    }

    fun setAnimatedCanvas(enabled: Boolean) = scope.launch {
        context.settingsStore.edit { it[Keys.animatedCanvas] = enabled }
    }

    fun setCanvasOverCellular(enabled: Boolean) = scope.launch {
        context.settingsStore.edit { it[Keys.canvasOverCellular] = enabled }
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

    suspend fun current(): AppSettings = settings.first()
}
