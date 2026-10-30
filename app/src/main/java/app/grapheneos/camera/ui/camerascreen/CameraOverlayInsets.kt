package app.grapheneos.camera.ui.camerascreen

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Distances from the screen's edges: to the preview, the shutter row and the bottom row. */
@Immutable
internal data class CameraOverlayInsets(
    val previewTop: Dp = 0.dp,
    val previewBottom: Dp = 0.dp,
    val shutterRow: Dp = 0.dp,
    val bottomRow: Dp = 0.dp,
)
