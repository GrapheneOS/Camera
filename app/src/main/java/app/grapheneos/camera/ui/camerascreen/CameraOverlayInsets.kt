package app.grapheneos.camera.ui.camerascreen

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp

/** Preview bounds and bottom-edge distances, all relative to the Compose overlay. */
@Immutable
internal data class CameraOverlayInsets(
    val previewBounds: DpRect = DpRect(
        left = 0.dp,
        top = 0.dp,
        right = 0.dp,
        bottom = 0.dp,
    ),
    val previewBottom: Dp = 0.dp,
    val shutterRow: Dp = 0.dp,
    val bottomRow: Dp = 0.dp,
)
