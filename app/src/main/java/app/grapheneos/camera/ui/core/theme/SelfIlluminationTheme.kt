package app.grapheneos.camera.ui.core.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val selfIlluminationColor = MaterialTheme.cameraColors.selfIllumination
    // TODO: follow the system theme once the viewfinder's own controls are Compose and do too.
    val darkColorScheme = remember(context) {
        cameraColorScheme(
            context = context,
            darkTheme = true,
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
        0f -> darkColorScheme
        1f -> litColorScheme
        else -> lerp(
            start = darkColorScheme,
            stop = litColorScheme,
            fraction = litFraction,
        )
    }

    val controlColors = MaterialTheme.cameraColors.copy(
        controlContainer = lerp(
            start = darkColorScheme.surfaceContainerHighest,
            stop = MaterialTheme.cameraColors.overlayScrim,
            fraction = litFraction,
        ),
        onControlContainer = lerp(
            start = darkColorScheme.onSurface,
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
