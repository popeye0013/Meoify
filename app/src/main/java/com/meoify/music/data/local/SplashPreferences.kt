package com.meoify.music.data.local

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.annotation.StyleRes
import com.meoify.music.R

/**
 * Synchronous splash-screen state.
 *
 * The system draws the splash before [SettingsStore]'s DataStore has been
 * read, so the dynamic-color flag is mirrored in SharedPreferences and the
 * starting theme is chosen in `MainActivity.onCreate`.
 *
 * On Android 12+ the chosen theme is also handed to the platform, which
 * persists it and uses it for the next launch's splash.
 */
object SplashPreferences {
    private const val PREFS = "meoify_splash"
    private const val KEY_DYNAMIC = "dynamic_color"
    private const val KEY_APPLIED_THEME = "applied_theme"

    fun isDynamicColorEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DYNAMIC, false)

    fun setDynamicColorEnabled(context: Context, enabled: Boolean) {
        // Always called off the main thread; commit so the flag survives even
        // if the process is killed right after the toggle.
        prefs(context).edit().putBoolean(KEY_DYNAMIC, enabled).commit()
    }

    /**
     * Persists the splash theme for the next launch. The platform only reads
     * the theme when it creates the starting window, so a change made now is
     * visible from the next launch onwards.
     */
    fun syncPlatformSplashTheme(context: Context, dynamic: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val activity = context.findActivity() ?: return
        val theme = splashTheme(dynamic)
        if (prefs(context).getInt(KEY_APPLIED_THEME, 0) == theme) return
        runCatching { activity.splashScreen.setSplashScreenTheme(theme) }
            .onSuccess { prefs(context).edit().putInt(KEY_APPLIED_THEME, theme).apply() }
    }

    @StyleRes
    fun splashTheme(dynamic: Boolean): Int =
        if (dynamic) R.style.Theme_App_Starting_Dynamic else R.style.Theme_App_Starting

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
