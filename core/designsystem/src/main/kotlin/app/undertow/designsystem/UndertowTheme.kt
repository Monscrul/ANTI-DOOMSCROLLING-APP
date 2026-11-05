package app.undertow.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = UndertowTokens.Ember,
    onPrimary = UndertowTokens.Ink,
    secondary = UndertowTokens.Depth,
    onSecondary = UndertowTokens.Paper,
    background = UndertowTokens.Ink,
    onBackground = UndertowTokens.Paper,
    surface = UndertowTokens.InkRaised,
    onSurface = UndertowTokens.Paper,
    surfaceVariant = UndertowTokens.InkRaised,
    onSurfaceVariant = UndertowTokens.Salt,
)

private val LightScheme = lightColorScheme(
    primary = UndertowTokens.Ember,
    onPrimary = UndertowTokens.Paper,
    secondary = UndertowTokens.Depth,
    onSecondary = UndertowTokens.Paper,
    background = UndertowTokens.Paper,
    onBackground = UndertowTokens.Ink,
    surface = UndertowTokens.PaperRaised,
    onSurface = UndertowTokens.Ink,
    surfaceVariant = UndertowTokens.PaperRaised,
    onSurfaceVariant = UndertowTokens.Salt,
)

@Composable
fun UndertowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
