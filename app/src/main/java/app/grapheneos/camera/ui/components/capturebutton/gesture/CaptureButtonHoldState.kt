package app.grapheneos.camera.ui.components.capturebutton.gesture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTarget

@Stable
internal class CaptureButtonHoldState {

    var isHeld by mutableStateOf(false)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var armedTarget by mutableStateOf<CaptureButtonTarget?>(null)
        private set

    internal fun start() {
        isHeld = true
    }

    internal fun move(
        offset: Offset,
        armedTarget: CaptureButtonTarget?,
    ) {
        this.offset = offset
        this.armedTarget = armedTarget
    }

    internal fun end() {
        isHeld = false
        offset = Offset.Zero
        armedTarget = null
    }
}

@Composable
internal fun rememberCaptureButtonHoldState(): CaptureButtonHoldState {
    return remember { CaptureButtonHoldState() }
}
