package app.grapheneos.camera.ui.components.shuttercore

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterTone
import app.grapheneos.camera.ui.core.cameraColors

@Immutable
internal data class ShutterCoreColors(
    val neutralColor: Color,
    val recordingColor: Color,
    val onNeutralColor: Color,
    val onRecordingColor: Color,
) {

    internal fun color(tone: ShutterTone): Color {
        return when (tone) {
            ShutterTone.Neutral -> neutralColor
            ShutterTone.Recording -> recordingColor
        }
    }

    internal fun contentColor(tone: ShutterTone): Color {
        return when (tone) {
            ShutterTone.Neutral -> onNeutralColor
            ShutterTone.Recording -> onRecordingColor
        }
    }

    companion object {
        @Composable
        @ReadOnlyComposable
        fun fromTheme(): ShutterCoreColors {
            val cameraColors = MaterialTheme.cameraColors

            return ShutterCoreColors(
                neutralColor = cameraColors.overlay,
                recordingColor = cameraColors.recording,
                onNeutralColor = cameraColors.onOverlay,
                onRecordingColor = cameraColors.overlay,
            )
        }
    }
}
