package app.grapheneos.camera.ui.components.focusindicator

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.grapheneos.camera.ui.core.cameraColors

@Immutable
internal data class FocusIndicatorColors(
    val ringColor: Color,
    val lockedColor: Color,
) {

    companion object {
        @Composable
        @ReadOnlyComposable
        fun fromTheme(): FocusIndicatorColors {
            val cameraColors = MaterialTheme.cameraColors

            return FocusIndicatorColors(
                ringColor = cameraColors.overlay,
                lockedColor = cameraColors.locked,
            )
        }
    }
}
