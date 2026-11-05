package app.undertow.designsystem

import androidx.compose.ui.graphics.Color

/**
 * The single source of raw colour values. Feature modules must reference
 * [androidx.compose.material3.MaterialTheme.colorScheme] instead of these
 * directly, so a palette change never means touching feature code.
 *
 * Palette: an analogue measuring instrument — ink and paper, one ember accent.
 */
object UndertowTokens {
    val Ink = Color(0xFF0B1A20)
    val InkRaised = Color(0xFF12262D)
    val Paper = Color(0xFFF2EDE4)
    val PaperRaised = Color(0xFFE7E0D4)
    val Ember = Color(0xFFE2653B)
    val Depth = Color(0xFF2E7F86)
    val Salt = Color(0xFF8FA6A8)
}
