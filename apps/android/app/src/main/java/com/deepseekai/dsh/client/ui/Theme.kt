package com.deepseekai.dsh.client.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Web dark-theme token values (packages/client/ui-theme/src/styles/design-platform.css,
// body[data-ds-dark-theme]); the app renders the same surface as the web hero.
val DswBgBase = Color(0xFF151517) // neutral-bluish-950
val DswBgLayer1 = Color(0xFF232324) // neutral-bluish-875
val DswBgLayer2 = Color(0xFF2C2C2E) // neutral-bluish-850 (input-major)
val DswBgLayer3 = Color(0xFF353638) // neutral-bluish-800 (selector)
val DswLabelPrimary = Color(0xFFF9FAFB) // neutral-bluish-50
val DswLabelSecondary = Color(0xFFCFD3D6) // neutral-bluish-300
val DswLabelCaption = Color(0xFF81858C) // neutral-bluish-600
val DswBorderThin = Color(0x0FFFFFFF) // rgba(255,255,255,0.06)
val DswBorder = Color(0x1FFFFFFF) // rgba(255,255,255,0.12)
val DswDeepSeek400 = Color(0xFF679EFE)
val DswDeepSeek500 = Color(0xFF4176E6)
val DswDeepSeek800 = Color(0xFF34415B) // preview badge fill
val DswGlow = Color(0xFF6187D8) // hero glow ellipse
val DswError = Color(0xFFF25A5A) // red-400

/**
 * The app's fixed dark surface: web dark tokens mapped onto a Material3
 * scheme, independent of the device setting so the hero composition stays
 * the web composition.
 */
@Composable
fun DshTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DswDeepSeek400,
            onPrimary = Color(0xFF0F1115),
            primaryContainer = DswDeepSeek500,
            onPrimaryContainer = Color(0xFFEAF0FE),
            secondary = DswLabelSecondary,
            onSecondary = DswBgBase,
            secondaryContainer = DswBgLayer3,
            onSecondaryContainer = DswLabelPrimary,
            tertiary = DswLabelPrimary,
            onTertiary = DswBgBase,
            background = DswBgBase,
            onBackground = DswLabelPrimary,
            surface = DswBgBase,
            onSurface = DswLabelPrimary,
            surfaceVariant = DswBgLayer2,
            onSurfaceVariant = DswLabelCaption,
            outline = DswBorder,
            outlineVariant = DswBorderThin,
            error = DswError,
            onError = Color.White,
            errorContainer = Color(0xFF5A2727),
            onErrorContainer = Color(0xFFFEF0F0),
        ),
        content = content,
    )
}
