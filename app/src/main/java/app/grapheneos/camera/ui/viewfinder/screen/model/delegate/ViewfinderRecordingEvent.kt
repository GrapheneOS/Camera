package app.grapheneos.camera.ui.viewfinder.screen.model.delegate

import android.net.Uri
import app.grapheneos.camera.data.media.model.CapturedItem

sealed interface ViewfinderRecordingEvent {

    data object OutputUnavailable : ViewfinderRecordingEvent

    data class CaptureResultSaved(
        val uri: Uri,
    ) : ViewfinderRecordingEvent

    data class Saved(
        val item: CapturedItem,
    ) : ViewfinderRecordingEvent
}
