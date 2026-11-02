package app.grapheneos.camera.ui.viewfinder.screen.model

import kotlin.time.Duration

data class ViewfinderCaptureState(
    val isCapturedPreviewShown: Boolean = false,
    val isTakingPicture: Boolean = false,
    val isSavingPicture: Boolean = false,
    val selfTimerRemaining: Duration? = null,
) {

    fun isSelfTimerRunning(): Boolean {
        return selfTimerRemaining != null
    }
}
