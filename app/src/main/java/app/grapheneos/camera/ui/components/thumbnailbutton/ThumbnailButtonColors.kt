package app.grapheneos.camera.ui.components.thumbnailbutton

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.grapheneos.camera.ui.components.squarebutton.SquareButtonColors

@Immutable
internal data class ThumbnailButtonColors(
    val buttonColors: SquareButtonColors,
    val progressColor: Color,
) {

    companion object {
        @Composable
        @ReadOnlyComposable
        fun fromTheme(): ThumbnailButtonColors {
            return ThumbnailButtonColors(
                buttonColors = SquareButtonColors.fromTheme(),
                progressColor = MaterialTheme.colorScheme.primaryFixedDim,
            )
        }

        @Composable
        @ReadOnlyComposable
        fun fromSurfaceTheme(): ThumbnailButtonColors {
            return ThumbnailButtonColors(
                buttonColors = SquareButtonColors.fromSurfaceTheme(),
                progressColor = MaterialTheme.colorScheme.primaryFixedDim,
            )
        }
    }
}
