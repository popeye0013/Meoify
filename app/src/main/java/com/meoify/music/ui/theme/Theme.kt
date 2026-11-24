package com.meoify.music.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.meoify.music.data.model.AppSettings
import com.meoify.music.data.model.ThemeMode

private fun meoifyDarkScheme(pureBlack: Boolean): ColorScheme {
    val bg = if (pureBlack) Ink else NearBlack
    val surface = if (pureBlack) Color(0xFF070708) else CardDark
    val low = if (pureBlack) Color(0xFF0B0B0C) else Color(0xFF121214)
    val mid = if (pureBlack) Color(0xFF111113) else Color(0xFF19191C)
    val high = if (pureBlack) Color(0xFF18181B) else Color(0xFF222226)
    val highest = if (pureBlack) Color(0xFF222226) else Color(0xFF2B2B30)

    return darkColorScheme(
        primary = Crimson,
        onPrimary = Color.White,
        primaryContainer = CrimsonContainer,
        onPrimaryContainer = Color(0xFFFFD9DE),
        inversePrimary = CrimsonDeep,
        secondary = Color(0xFFE9BFC4),
        onSecondary = Color(0xFF44292C),
        secondaryContainer = Color(0xFF5D3F43),
        onSecondaryContainer = Color(0xFFFFD9DE),
        tertiary = Color(0xFFE7C46C),
        onTertiary = Color(0xFF3D2F00),
        tertiaryContainer = Color(0xFF574500),
        onTertiaryContainer = Color(0xFFFFE08A),
        background = bg,
        onBackground = Color(0xFFF2F0F2),
        surface = surface,
        onSurface = Color(0xFFF2F0F2),
        surfaceVariant = CardRaised,
        onSurfaceVariant = TextSecondary,
        surfaceTint = Crimson,
        surfaceDim = bg,
        surfaceBright = highest,
        surfaceContainerLowest = bg,
        surfaceContainerLow = low,
        surfaceContainer = mid,
        surfaceContainerHigh = high,
        surfaceContainerHighest = highest,
        outline = Color(0xFF938F94),
        outlineVariant = Hairline,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        inverseSurface = Color(0xFFF2F0F2),
        inverseOnSurface = Color(0xFF1B1B1E),
        scrim = Color.Black,
    )
}

private val MeoifyLightScheme = lightColorScheme(
    primary = CrimsonDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDADF),
    onPrimaryContainer = Color(0xFF41000D),
    inversePrimary = Crimson,
    secondary = Color(0xFF77565A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDADF),
    onSecondaryContainer = Color(0xFF2C1518),
    tertiary = Color(0xFF745B00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color(0xFF241A00),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF201A1B),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF201A1B),
    surfaceVariant = Color(0xFFF4DDDF),
    onSurfaceVariant = Color(0xFF534345),
    surfaceTint = CrimsonDeep,
    surfaceDim = Color(0xFFE8D6D7),
    surfaceBright = Color(0xFFFFF8F7),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF0F0),
    surfaceContainer = Color(0xFFFCEAEA),
    surfaceContainerHigh = Color(0xFFF6E4E5),
    surfaceContainerHighest = Color(0xFFF0DEDF),
    outline = Color(0xFF857375),
    outlineVariant = Color(0xFFD7C1C3),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF362F30),
    inverseOnSurface = Color(0xFFFBEDEE),
    scrim = Color.Black,
)

@Composable
fun MeoifyTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val context = LocalContext.current
    val colorScheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (!darkTheme) {
                dynamicLightColorScheme(context)
            } else {
                val dynamic = dynamicDarkColorScheme(context)
                if (!settings.pureBlack) {
                    dynamic
                } else {
                    // Dynamic palettes ignore the pure-black surfaces. Keep the
                    // wallpaper-derived accents but swap in the true-black
                    // backgrounds/containers so the preference still applies.
                    val pure = meoifyDarkScheme(pureBlack = true)
                    dynamic.copy(
                        background = pure.background,
                        onBackground = pure.onBackground,
                        surface = pure.surface,
                        onSurface = pure.onSurface,
                        surfaceVariant = pure.surfaceVariant,
                        onSurfaceVariant = pure.onSurfaceVariant,
                        surfaceDim = pure.surfaceDim,
                        surfaceBright = pure.surfaceBright,
                        surfaceContainerLowest = pure.surfaceContainerLowest,
                        surfaceContainerLow = pure.surfaceContainerLow,
                        surfaceContainer = pure.surfaceContainer,
                        surfaceContainerHigh = pure.surfaceContainerHigh,
                        surfaceContainerHighest = pure.surfaceContainerHighest,
                        inverseSurface = pure.inverseSurface,
                        inverseOnSurface = pure.inverseOnSurface,
                        scrim = pure.scrim,
                    )
                }
            }
        }

        darkTheme -> meoifyDarkScheme(settings.pureBlack)
        else -> MeoifyLightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MeoifyTypography,
        shapes = MeoifyShapes,
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
