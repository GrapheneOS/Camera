package app.grapheneos.camera.ui.components.adjustmentbar

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.grapheneos.camera.ui.core.cameraColors

@Immutable
internal data class AdjustmentBarColors(
    val containerColor: Color,
    val tickColor: Color,
    val majorTickColor: Color,
    val indicatorColor: Color,
    val startIconColor: Color,
    val endIconColor: Color,
    val focusColor: Color,
) {

    companion object {
        @Composable
        @ReadOnlyComposable
        fun fromTheme(): AdjustmentBarColors {
            val cameraColors = MaterialTheme.cameraColors

            return AdjustmentBarColors(
                containerColor = cameraColors.overlayScrim,
                tickColor = cameraColors.overlay,
                majorTickColor = cameraColors.overlayAccent,
                indicatorColor = cameraColors.overlayAccent,
                startIconColor = cameraColors.overlayAccent,
                endIconColor = cameraColors.overlayAccent,
                focusColor = cameraColors.overlay,
            )
        }
    }
}
