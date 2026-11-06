package com.tcc.devicehealth.agent

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val lightColors = lightColorScheme(
    primary = Color(0xFF255FDB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EBFF),
    onPrimaryContainer = Color(0xFF123479),
    secondaryContainer = Color(0xFFE3EBFF),
    onSecondaryContainer = Color(0xFF123479),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF151C2C),
    surface = Color.White,
    onSurface = Color(0xFF151C2C),
    surfaceVariant = Color(0xFFE8ECF3),
    onSurfaceVariant = Color(0xFF4F5B70),
    outline = Color(0xFF79859A),
    outlineVariant = Color(0xFFD9DFEA),
    error = Color(0xFFAA3027),
    errorContainer = Color(0xFFFDE7E4),
    onErrorContainer = Color(0xFF6E1710),
)

private val darkColors = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF0E2D6E),
    primaryContainer = Color(0xFF1D4094),
    onPrimaryContainer = Color(0xFFDDE6FF),
    secondaryContainer = Color(0xFF26324C),
    onSecondaryContainer = Color(0xFFDDE6FF),
    background = Color(0xFF0F1420),
    onBackground = Color(0xFFE3E7F0),
    surface = Color(0xFF181F2E),
    onSurface = Color(0xFFE3E7F0),
    surfaceVariant = Color(0xFF242B3A),
    onSurfaceVariant = Color(0xFFB7C0D1),
    outline = Color(0xFF8992A4),
    outlineVariant = Color(0xFF303849),
    error = Color(0xFFFFB4A9),
    errorContainer = Color(0xFF5C1B14),
    onErrorContainer = Color(0xFFFFDAD4),
)

private val deviceHealthTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.2.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp),
    )
}

internal val metricTextStyle = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)

@Immutable
data class StatusColors(
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

private val lightStatusColors = StatusColors(
    successContainer = Color(0xFFDDF4E6),
    onSuccessContainer = Color(0xFF115C33),
    warningContainer = Color(0xFFFFF0CC),
    onWarningContainer = Color(0xFF6E4C00),
)

private val darkStatusColors = StatusColors(
    successContainer = Color(0xFF173D28),
    onSuccessContainer = Color(0xFFB4EDC8),
    warningContainer = Color(0xFF433300),
    onWarningContainer = Color(0xFFFFDF94),
)

internal val LocalStatusColors = staticCompositionLocalOf { lightStatusColors }

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun DeviceHealthTheme(themeMode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = themeMode.isDark()
    CompositionLocalProvider(LocalStatusColors provides if (dark) darkStatusColors else lightStatusColors) {
        MaterialTheme(
            colorScheme = if (dark) darkColors else lightColors,
            typography = deviceHealthTypography,
            content = content,
        )
    }
}
