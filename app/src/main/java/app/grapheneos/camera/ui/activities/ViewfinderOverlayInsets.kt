package app.grapheneos.camera.ui.activities

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Distances from the screen's edges: to the preview, the shutter row and the bottom row. */
@Immutable
internal data class ViewfinderOverlayInsets(
    val previewTop: Dp = 0.dp,
    val previewBottom: Dp = 0.dp,
    val shutterRow: Dp = 0.dp,
    val bottomRow: Dp = 0.dp,
)
