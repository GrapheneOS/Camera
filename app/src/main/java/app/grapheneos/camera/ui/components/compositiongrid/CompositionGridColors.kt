package app.grapheneos.camera.ui.components.compositiongrid

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.grapheneos.camera.ui.core.cameraColors

@Immutable
internal data class CompositionGridColors(
    val lineColor: Color,
) {

    companion object {
        @Composable
        @ReadOnlyComposable
        fun fromTheme(): CompositionGridColors {
            return CompositionGridColors(
                lineColor = MaterialTheme.cameraColors.overlay,
            )
        }
    }
}
