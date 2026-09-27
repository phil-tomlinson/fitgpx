/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.philtomlinson.fitgpx.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A63), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9EF2E7), onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF4A635F), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E2), onSecondaryContainer = Color(0xFF05201C),
    tertiary = Color(0xFF8B5000), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBE), onTertiaryContainer = Color(0xFF2C1600),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4FBF9), onBackground = Color(0xFF161D1C),
    surface = Color(0xFFF4FBF9), onSurface = Color(0xFF161D1C),
    surfaceVariant = Color(0xFFDAE5E1), onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977), outlineVariant = Color(0xFFBEC9C6),
    inverseSurface = Color(0xFF2B3231), inverseOnSurface = Color(0xFFECF2F0), inversePrimary = Color(0xFF82D5CB),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEEF5F3),
    surfaceContainer = Color(0xFFE8EFED), surfaceContainerHigh = Color(0xFFE3EAE8),
    surfaceContainerHighest = Color(0xFFDDE4E2),
    surfaceDim = Color(0xFFD5DBD9), surfaceBright = Color(0xFFF4FBF9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D5CB), onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504A), onPrimaryContainer = Color(0xFF9EF2E7),
    secondary = Color(0xFFB0CCC6), onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF334B47), onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFFFB870), onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00), onTertiaryContainer = Color(0xFFFFDCBE),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1514), onBackground = Color(0xFFDDE4E2),
    surface = Color(0xFF0E1514), onSurface = Color(0xFFDDE4E2),
    surfaceVariant = Color(0xFF3F4947), onSurfaceVariant = Color(0xFFBEC9C6),
    outline = Color(0xFF899390), outlineVariant = Color(0xFF3F4947),
    inverseSurface = Color(0xFFDDE4E2), inverseOnSurface = Color(0xFF2B3231), inversePrimary = Color(0xFF006A63),
    surfaceContainerLowest = Color(0xFF090F0F), surfaceContainerLow = Color(0xFF161D1C),
    surfaceContainer = Color(0xFF1A2120), surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF2F3635),
    surfaceDim = Color(0xFF0E1514), surfaceBright = Color(0xFF343B3A),
)

/** Colors outside the Material scheme: the route line and data series. */
@Immutable
data class FitGpxColors(
    val track: Color,
    val trackOutline: Color,
    val trackMuted: Color,
    val start: Color,
    val finish: Color,
    val heartRate: Color,
    val elevation: Color,
    val speed: Color,
    val success: Color,
    val warning: Color,
    val privacyZone: Color,
)

private val LightExtra = FitGpxColors(
    track = Color(0xFFE8590C), trackOutline = Color(0xFFFFFFFF), trackMuted = Color(0xFF8A9491),
    start = Color(0xFF1B8A3A), finish = Color(0xFFC62828),
    heartRate = Color(0xFFD6336C), elevation = Color(0xFF00897B), speed = Color(0xFF3B5BDB),
    success = Color(0xFF2B8A3E), warning = Color(0xFFB35C00), privacyZone = Color(0xFF7048E8),
)

private val DarkExtra = FitGpxColors(
    track = Color(0xFFFF8A3D), trackOutline = Color(0xFF0E1514), trackMuted = Color(0xFF6F7977),
    start = Color(0xFF69DB7C), finish = Color(0xFFFF8787),
    heartRate = Color(0xFFF783AC), elevation = Color(0xFF63E6BE), speed = Color(0xFF91A7FF),
    success = Color(0xFF8CE99A), warning = Color(0xFFFFC078), privacyZone = Color(0xFFB197FC),
)

val LocalFitGpxColors = staticCompositionLocalOf { LightExtra }

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Tabular figures so numbers don't jiggle while dragging trim handles. */
val NumberStyle = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun FitGpxTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalFitGpxColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

object FitGpxTheme {
    val colors: FitGpxColors
        @Composable get() = LocalFitGpxColors.current
}
