package com.viewtube.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.viewtube.app.data.Graph

val YtRed = Color(0xFFE53935)
val YtChip = Color(0xFFF0EAF6)
val YtChipDark = Color(0xFF302B3A)

private val ExpressiveShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val Light = lightColorScheme(
    primary = Color(0xFFB3261E),
    onPrimary = Color.White,
    secondary = Color(0xFF6750A4),
    onSecondary = Color.White,
    tertiary = Color(0xFF006B5F),
    background = Color(0xFFFFF8F7),
    surface = Color(0xFFFFF8F7),
    surfaceVariant = YtChip,
    onSurfaceVariant = Color(0xFF514A57),
    outline = Color(0xFF817782),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF690005),
    secondary = Color(0xFFD0BCFF),
    onSecondary = Color(0xFF381E72),
    tertiary = Color(0xFF80D5C5),
    background = Color(0xFF141218),
    surface = Color(0xFF1D1A22),
    surfaceVariant = YtChipDark,
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938D99),
)

@Composable
fun ViewTubeTheme(content: @Composable () -> Unit) {
    val mode by Graph.store.themeMode.collectAsStateCompat()
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) Dark else Light
    MaterialTheme(
        colorScheme = scheme,
        shapes = ExpressiveShapes,
        content = content
    )
}
