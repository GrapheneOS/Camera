package app.grapheneos.camera.ui.components.overlayiconbutton

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.grapheneos.camera.ui.core.cameraColors

@Immutable
internal data class OverlayIconToggleButtonColors(
    val buttonColors: OverlayIconButtonColors,
    val checkedContainerColor: Color,
    val checkedContentColor: Color,
) {

    companion object {
        @Composable
        @ReadOnlyComposable
        fun fromTheme(): OverlayIconToggleButtonColors {
            val cameraColors = MaterialTheme.cameraColors

            return OverlayIconToggleButtonColors(
                buttonColors = OverlayIconButtonColors.fromTheme(),
                checkedContainerColor = cameraColors.overlay,
                checkedContentColor = cameraColors.onOverlay,
            )
        }
    }
}
