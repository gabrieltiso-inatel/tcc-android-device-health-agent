package com.tcc.devicehealth.agent

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val deviceHealthColors = lightColorScheme(
    primary = Color(0xFF255FDB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF0FF),
    onPrimaryContainer = Color(0xFF183D8B),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF172033),
    surface = Color.White,
    onSurface = Color(0xFF172033),
    surfaceVariant = Color(0xFFEDF1F7),
    onSurfaceVariant = Color(0xFF536178),
    outline = Color(0xFF718096),
    error = Color(0xFFAA3027),
)

@Composable
fun DeviceHealthTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = deviceHealthColors, content = content)
}
