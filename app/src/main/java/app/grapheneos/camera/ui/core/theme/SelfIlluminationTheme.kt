package app.grapheneos.camera.ui.core.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun SelfIlluminationTheme(
    selfIlluminating: Boolean,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val selfIlluminationColor = MaterialTheme.cameraColors.selfIllumination
    val baseColorScheme = remember(context, darkTheme) {
        cameraColorScheme(
            context = context,
            darkTheme = darkTheme,
        )
    }
    val litColorScheme = remember(context, selfIlluminationColor) {
        cameraColorScheme(
            context = context,
            darkTheme = false,
        ).copy(surfaceContainer = selfIlluminationColor)
    }
    val litFraction by animateFloatAsState(
        targetValue = when {
            selfIlluminating -> 1f
            else -> 0f
        },
        animationSpec = tween(),
    )

    val colorScheme = when (litFraction) {
        0f -> baseColorScheme
        1f -> litColorScheme
        else -> lerp(
            start = baseColorScheme,
            stop = litColorScheme,
            fraction = litFraction,
        )
    }

    val controlColors = MaterialTheme.cameraColors.copy(
        controlContainer = lerp(
            start = baseColorScheme.surfaceContainerHighest,
            stop = MaterialTheme.cameraColors.overlayScrim,
            fraction = litFraction,
        ),
        onControlContainer = lerp(
            start = baseColorScheme.onSurface,
            stop = MaterialTheme.cameraColors.overlay,
            fraction = litFraction,
        ),
    )

    CompositionLocalProvider(LocalCameraColors provides controlColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
