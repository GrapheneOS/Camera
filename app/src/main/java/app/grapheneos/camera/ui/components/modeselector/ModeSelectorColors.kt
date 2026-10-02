package app.grapheneos.camera.ui.components.modeselector

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.grapheneos.camera.ui.core.cameraColors

@Immutable
internal data class ModeSelectorColors(
    val contentColor: Color,
    val selectedContainerColor: Color,
    val selectedContentColor: Color,
) {

    companion object {
        @Composable
        @ReadOnlyComposable
        fun fromTheme(): ModeSelectorColors {
            val colorScheme = MaterialTheme.colorScheme

            return ModeSelectorColors(
                contentColor = MaterialTheme.cameraColors.overlay,
                selectedContainerColor = colorScheme.primaryFixed,
                selectedContentColor = colorScheme.onPrimaryFixed,
            )
        }
    }
}
