package io.mirr.plexplay.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PlexGold = Color(0xFFE5A00D)
val Ink = Color.Black
val Panel = Color(0xFF14161A)
val Muted = Color(0xFF9A9DA4)

private val DarkColors = darkColorScheme(
    primary = PlexGold,
    onPrimary = Color(0xFF1D1400),
    primaryContainer = Color(0xFF3B2A05),
    onPrimaryContainer = Color(0xFFFFDEA0),
    background = Ink,
    onBackground = Color(0xFFF2F2F3),
    surface = Panel,
    onSurface = Color(0xFFF2F2F3),
    surfaceVariant = Color(0xFF202329),
    onSurfaceVariant = Color(0xFFC3C5CB),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF805600),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDA4),
    onPrimaryContainer = Color(0xFF291800),
    background = Color(0xFFFFF8F2),
    onBackground = Color(0xFF211B13),
    surface = Color.White,
    onSurface = Color(0xFF211B13),
    surfaceVariant = Color(0xFFF1E5D8),
    onSurfaceVariant = Color(0xFF504539),
)

@Composable
fun PlexPlayTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
