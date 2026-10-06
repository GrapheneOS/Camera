package app.grapheneos.camera.ui.components.valuechip

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.grapheneos.camera.ui.core.cameraColors

@Immutable
internal data class ValueChipColors(
    val containerColor: Color,
    val contentColor: Color,
) {

    companion object {
        @Composable
        @ReadOnlyComposable
        fun fromTheme(): ValueChipColors {
            val colorScheme = MaterialTheme.colorScheme

            return ValueChipColors(
                containerColor = colorScheme.primaryFixed,
                contentColor = colorScheme.onPrimaryFixed,
            )
        }

        @Composable
        @ReadOnlyComposable
        fun fromRecordingTheme(): ValueChipColors {
            val cameraColors = MaterialTheme.cameraColors

            return ValueChipColors(
                containerColor = cameraColors.recording,
                contentColor = cameraColors.overlay,
            )
        }
    }
}
