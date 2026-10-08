package app.grapheneos.camera.ui.components.levelindicator

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.grapheneos.camera.ui.core.cameraColors

@Immutable
internal data class LevelIndicatorColors(
    val lineColor: Color,
    val levelColor: Color,
    val shadowColor: Color,
) {

    companion object {
        @Composable
        @ReadOnlyComposable
        fun fromTheme(): LevelIndicatorColors {
            val cameraColors = MaterialTheme.cameraColors

            return LevelIndicatorColors(
                lineColor = cameraColors.overlay,
                levelColor = cameraColors.locked,
                shadowColor = cameraColors.overlayScrim,
            )
        }
    }
}
