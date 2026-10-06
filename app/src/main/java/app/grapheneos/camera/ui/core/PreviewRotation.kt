package app.grapheneos.camera.ui.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

@Stable
internal class PreviewRotation {

    var degrees by mutableFloatStateOf(0f)
        private set

    fun rotate() {
        degrees = (degrees + QUARTER_TURN) % FULL_TURN
    }

    @Composable
    fun Control() {
        CameraPreviewControl(
            text = "Rotate ${degrees.toInt()}°",
            onClick = ::rotate,
        )
    }

    private companion object {
        private const val QUARTER_TURN = 90f
        private const val FULL_TURN = 360f
    }
}
