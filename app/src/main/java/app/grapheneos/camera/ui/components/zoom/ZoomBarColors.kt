package app.grapheneos.camera.ui.components.zoom

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.grapheneos.camera.ui.core.cameraColors

@Immutable
internal data class ZoomBarColors(
    val containerColor: Color,
    val tickColor: Color,
    val stopColor: Color,
    val indicatorColor: Color,
    val focusColor: Color,
) {

    companion object {
        @Composable
        @ReadOnlyComposable
        fun fromTheme(): ZoomBarColors {
            val cameraColors = MaterialTheme.cameraColors
            val accent = MaterialTheme.colorScheme.primaryFixedDim

            return ZoomBarColors(
                containerColor = cameraColors.overlayScrim,
                tickColor = cameraColors.overlay,
                stopColor = accent,
                indicatorColor = accent,
                focusColor = cameraColors.overlay,
            )
        }
    }
}
