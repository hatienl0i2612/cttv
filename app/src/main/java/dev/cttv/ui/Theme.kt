package dev.cttv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.darkColorScheme

val Background = Color(0xFF10131A)
val Panel = Color(0xFF1C2230)
val Muted = Color(0xFF9DA8BB)
val Accent = Color(0xFFF4A261)

@Composable
fun CttvTheme(content: @Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Accent, background = Background, surface = Panel, onSurface = Color.White,
        ),
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Accent, onPrimary = Background,
                background = Background, surface = Panel, onSurface = Color.White,
            ),
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
        }
    }
}
