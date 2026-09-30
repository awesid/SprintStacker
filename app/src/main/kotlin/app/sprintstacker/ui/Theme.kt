package app.sprintstacker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Page = Color(0xFF0D0A1C)
    val Ink = Color(0xFF181335)
    val Panel = Color(0xFF15112B)
    val Panel2 = Color(0xFF1B1636)
    val Line = Color(0xFF2B2452)
    val Text = Color(0xFFF2EDFF)
    val Muted = Color(0xFFA39BCB)
    val Faint = Color(0xFF6F679B)
    val Tangerine = Color(0xFFFF9F43)
    val TangerineShadow = Color(0xFFB3601A)
    val Aqua = Color(0xFF3BD6C6)
    val Lemon = Color(0xFFFFD84D)
    val Sky = Color(0xFF6CA8FF)
    val Hazard = Color(0xFFFF4D6D)
    val HazardDark = Color(0xFFB3203F)

    /** Square block colors, indexed by Block.colorIndex. */
    val Blocks = listOf(Tangerine, Aqua, Lemon, Sky)
}

@Composable
fun SprintStackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Tangerine,
            onPrimary = Color(0xFF2A1300),
            secondary = Palette.Aqua,
            error = Palette.Hazard,
            background = Palette.Ink,
            onBackground = Palette.Text,
            surface = Palette.Panel,
            onSurface = Palette.Text,
            surfaceVariant = Palette.Panel2,
            onSurfaceVariant = Palette.Muted,
            outline = Palette.Line,
        ),
        content = content,
    )
}
