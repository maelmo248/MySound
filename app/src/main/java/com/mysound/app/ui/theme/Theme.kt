package com.mysound.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MySoundDarkColorScheme = darkColorScheme(
    primary = BlueAccent,
    onPrimary = OnBlueAccent,
    primaryContainer = BlueAccentDark,
    secondary = BlueAccent,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary
)

@Composable
fun MySoundTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // L'app est pensée pour un thème sombre bleu ; on force ce thème
    // indépendamment du thème système pour rester fidèle à la maquette.
    MaterialTheme(
        colorScheme = MySoundDarkColorScheme,
        typography = MySoundTypography,
        content = content
    )
}
