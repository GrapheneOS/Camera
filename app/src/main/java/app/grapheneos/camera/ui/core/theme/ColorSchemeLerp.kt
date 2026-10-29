package app.grapheneos.camera.ui.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.lerp

internal fun lerp(
    start: ColorScheme,
    stop: ColorScheme,
    fraction: Float,
): ColorScheme {
    return start.copy(
        primary = lerp(start.primary, stop.primary, fraction),
        onPrimary = lerp(start.onPrimary, stop.onPrimary, fraction),
        primaryContainer = lerp(start.primaryContainer, stop.primaryContainer, fraction),
        onPrimaryContainer = lerp(start.onPrimaryContainer, stop.onPrimaryContainer, fraction),
        inversePrimary = lerp(start.inversePrimary, stop.inversePrimary, fraction),
        secondary = lerp(start.secondary, stop.secondary, fraction),
        onSecondary = lerp(start.onSecondary, stop.onSecondary, fraction),
        secondaryContainer = lerp(start.secondaryContainer, stop.secondaryContainer, fraction),
        onSecondaryContainer = lerp(
            start.onSecondaryContainer,
            stop.onSecondaryContainer,
            fraction,
        ),
        tertiary = lerp(start.tertiary, stop.tertiary, fraction),
        onTertiary = lerp(start.onTertiary, stop.onTertiary, fraction),
        tertiaryContainer = lerp(start.tertiaryContainer, stop.tertiaryContainer, fraction),
        onTertiaryContainer = lerp(start.onTertiaryContainer, stop.onTertiaryContainer, fraction),
        background = lerp(start.background, stop.background, fraction),
        onBackground = lerp(start.onBackground, stop.onBackground, fraction),
        surface = lerp(start.surface, stop.surface, fraction),
        onSurface = lerp(start.onSurface, stop.onSurface, fraction),
        surfaceVariant = lerp(start.surfaceVariant, stop.surfaceVariant, fraction),
        onSurfaceVariant = lerp(start.onSurfaceVariant, stop.onSurfaceVariant, fraction),
        surfaceTint = lerp(start.surfaceTint, stop.surfaceTint, fraction),
        inverseSurface = lerp(start.inverseSurface, stop.inverseSurface, fraction),
        inverseOnSurface = lerp(start.inverseOnSurface, stop.inverseOnSurface, fraction),
        error = lerp(start.error, stop.error, fraction),
        onError = lerp(start.onError, stop.onError, fraction),
        errorContainer = lerp(start.errorContainer, stop.errorContainer, fraction),
        onErrorContainer = lerp(start.onErrorContainer, stop.onErrorContainer, fraction),
        outline = lerp(start.outline, stop.outline, fraction),
        outlineVariant = lerp(start.outlineVariant, stop.outlineVariant, fraction),
        scrim = lerp(start.scrim, stop.scrim, fraction),
        surfaceBright = lerp(start.surfaceBright, stop.surfaceBright, fraction),
        surfaceDim = lerp(start.surfaceDim, stop.surfaceDim, fraction),
        surfaceContainer = lerp(start.surfaceContainer, stop.surfaceContainer, fraction),
        surfaceContainerHigh = lerp(
            start.surfaceContainerHigh,
            stop.surfaceContainerHigh,
            fraction,
        ),
        surfaceContainerHighest = lerp(
            start.surfaceContainerHighest,
            stop.surfaceContainerHighest,
            fraction,
        ),
        surfaceContainerLow = lerp(start.surfaceContainerLow, stop.surfaceContainerLow, fraction),
        surfaceContainerLowest = lerp(
            start.surfaceContainerLowest,
            stop.surfaceContainerLowest,
            fraction,
        ),
    )
}
