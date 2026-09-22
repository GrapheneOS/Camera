package app.grapheneos.camera.domain.capture.model

import android.location.Location

sealed interface CaptureLocation {

    data object NotRequested : CaptureLocation

    data object Unavailable : CaptureLocation

    data class Found(
        val location: Location,
    ) : CaptureLocation
}
