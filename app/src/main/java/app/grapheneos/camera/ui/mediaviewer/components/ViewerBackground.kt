package app.grapheneos.camera.ui.mediaviewer.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import app.grapheneos.camera.ui.core.cameraColors

@Composable
internal fun animateViewerBackgroundAsState(areControlsVisible: Boolean): State<Color> {
    return animateColorAsState(
        targetValue = when {
            areControlsVisible -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.cameraColors.onOverlay
        },
    )
}
