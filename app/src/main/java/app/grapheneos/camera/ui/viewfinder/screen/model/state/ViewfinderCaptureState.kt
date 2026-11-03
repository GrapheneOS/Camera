package app.grapheneos.camera.ui.viewfinder.screen.model.state

import kotlin.time.Duration

data class ViewfinderCaptureState(
    val result: ViewfinderCaptureResult? = null,
    val isShutterHeld: Boolean = false,
    val isTakingPicture: Boolean = false,
    val isSavingPicture: Boolean = false,
    val selfTimerRemaining: Duration? = null,
) {

    fun hasResult(): Boolean {
        return result != null
    }

    fun isSelfTimerRunning(): Boolean {
        return selfTimerRemaining != null
    }
}
