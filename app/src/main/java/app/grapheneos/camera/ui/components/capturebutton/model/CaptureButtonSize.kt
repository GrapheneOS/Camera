package app.grapheneos.camera.ui.components.capturebutton.model

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class CaptureButtonSize(
    internal val diameter: Dp,
) {
    Regular(diameter = 84.dp),
    Small(diameter = 58.dp),
}
