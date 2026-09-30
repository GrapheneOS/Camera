package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonCore
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTone
import app.grapheneos.camera.ui.core.cameraColors

@Immutable
internal data class CaptureButtonColors(
    val containerColor: Color,
    val coreColor: Color,
    val recordingCoreColor: Color,
    val contentColor: Color,
    val coreContentColor: Color,
    val focusColor: Color,
    val progressColor: Color,
    val progressTrackColor: Color,
) {

    internal fun coreColor(tone: CaptureButtonTone): Color {
        return when (tone) {
            CaptureButtonTone.Neutral -> coreColor
            CaptureButtonTone.Recording -> recordingCoreColor
        }
    }

    internal fun contentColor(core: CaptureButtonCore): Color {
        return when (core) {
            CaptureButtonCore.None -> contentColor
            else -> coreContentColor
        }
    }

    companion object {
        private const val PROGRESS_TRACK_ALPHA = 0.3f

        @Composable
        @ReadOnlyComposable
        fun fromTheme(): CaptureButtonColors {
            val cameraColors = MaterialTheme.cameraColors

            return CaptureButtonColors(
                containerColor = cameraColors.overlayScrim,
                coreColor = cameraColors.overlay,
                recordingCoreColor = cameraColors.recording,
                contentColor = cameraColors.overlay,
                coreContentColor = cameraColors.onOverlay,
                focusColor = cameraColors.overlay,
                progressColor = cameraColors.overlay,
                progressTrackColor = cameraColors.overlay.copy(alpha = PROGRESS_TRACK_ALPHA),
            )
        }
    }
}
