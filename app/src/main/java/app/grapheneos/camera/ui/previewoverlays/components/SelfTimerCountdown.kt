package app.grapheneos.camera.ui.previewoverlays.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import app.grapheneos.camera.ui.components.countdowntimer.CountDownTimer
import app.grapheneos.camera.ui.core.preview.CameraPreviewColumn
import app.grapheneos.camera.ui.core.preview.CameraPreviewViewfinder
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun SelfTimerCountdown(
    remaining: Duration,
    modifier: Modifier = Modifier,
) {
    CountDownTimer(
        value = ceil(remaining / 1.seconds).toInt(),
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun SelfTimerCountdownPreview() {
    CameraPreviewColumn {
        CameraPreviewViewfinder {
            SelfTimerCountdown(
                remaining = 3.seconds,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
