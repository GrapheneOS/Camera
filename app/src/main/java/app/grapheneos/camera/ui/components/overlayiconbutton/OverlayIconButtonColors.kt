package app.grapheneos.camera.ui.components.overlayiconbutton

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.grapheneos.camera.ui.core.cameraColors

@Immutable
internal data class OverlayIconButtonColors(
    val containerColor: Color,
    val contentColor: Color,
) {

    companion object {
        @Composable
        @ReadOnlyComposable
        fun fromTheme(): OverlayIconButtonColors {
            val cameraColors = MaterialTheme.cameraColors

            return OverlayIconButtonColors(
                containerColor = cameraColors.overlayScrim,
                contentColor = cameraColors.overlay,
            )
        }
    }
}
