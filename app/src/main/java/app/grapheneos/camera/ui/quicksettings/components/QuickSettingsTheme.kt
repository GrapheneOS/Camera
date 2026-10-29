package app.grapheneos.camera.ui.quicksettings.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.grapheneos.camera.ui.core.theme.cameraColorScheme
import app.grapheneos.camera.ui.core.theme.cameraColors
import app.grapheneos.camera.ui.core.theme.lerp

@Composable
internal fun QuickSettingsTheme(
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

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
