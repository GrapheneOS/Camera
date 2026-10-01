package app.grapheneos.camera.ui.components.countdowntimer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.grapheneos.camera.ui.core.cameraColors

@Immutable
internal data class CountDownTimerColors(
    val contentColor: Color,
) {

    companion object {
        @Composable
        @ReadOnlyComposable
        fun fromTheme(): CountDownTimerColors {
            return CountDownTimerColors(
                contentColor = MaterialTheme.cameraColors.overlay,
            )
        }
    }
}
