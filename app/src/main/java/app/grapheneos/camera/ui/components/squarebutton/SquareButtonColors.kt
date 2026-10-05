package app.grapheneos.camera.ui.components.squarebutton

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.grapheneos.camera.ui.core.cameraColors

@Immutable
internal data class SquareButtonColors(
    val containerColor: Color,
    val contentColor: Color,
) {

    companion object {
        @Composable
        @ReadOnlyComposable
        fun fromTheme(): SquareButtonColors {
            val cameraColors = MaterialTheme.cameraColors

            return SquareButtonColors(
                containerColor = cameraColors.overlayScrim,
                contentColor = cameraColors.overlay,
            )
        }

        @Composable
        @ReadOnlyComposable
        fun fromSurfaceTheme(): SquareButtonColors {
            val colorScheme = MaterialTheme.colorScheme

            return SquareButtonColors(
                containerColor = colorScheme.surfaceContainerHighest,
                contentColor = colorScheme.onSurface,
            )
        }
    }
}
