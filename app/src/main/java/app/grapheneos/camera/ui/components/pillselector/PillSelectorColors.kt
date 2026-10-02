package app.grapheneos.camera.ui.components.pillselector

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.grapheneos.camera.ui.core.cameraColors

@Immutable
internal data class PillSelectorColors(
    val containerColor: Color,
    val contentColor: Color,
    val selectedContainerColor: Color,
    val selectedContentColor: Color,
) {

    companion object {
        @Composable
        @ReadOnlyComposable
        fun fromTheme(): PillSelectorColors {
            val cameraColors = MaterialTheme.cameraColors
            val colorScheme = MaterialTheme.colorScheme

            return PillSelectorColors(
                containerColor = cameraColors.overlayScrim,
                contentColor = cameraColors.overlay,
                selectedContainerColor = colorScheme.primaryFixed,
                selectedContentColor = colorScheme.onPrimaryFixed,
            )
        }
    }
}
