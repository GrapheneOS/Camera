package app.grapheneos.camera.ui.viewfinder.screen.model

import app.grapheneos.camera.data.media.model.CapturedItem

sealed interface ViewfinderRecordingEvent {

    data object OutputUnavailable : ViewfinderRecordingEvent

    data class Saved(
        val item: CapturedItem,
    ) : ViewfinderRecordingEvent
}
