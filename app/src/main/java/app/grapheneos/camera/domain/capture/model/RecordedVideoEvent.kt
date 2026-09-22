package app.grapheneos.camera.domain.capture.model

import android.net.Uri
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.data.camera.model.RecordingOutcome
import kotlin.time.Duration

sealed interface RecordedVideoEvent {

    data object OutputUnavailable : RecordedVideoEvent

    data object LocationUnavailable : RecordedVideoEvent

    data object ReadyToStart : RecordedVideoEvent

    data object Abandoned : RecordedVideoEvent

    data object Started : RecordedVideoEvent

    data object SaveFailed : RecordedVideoEvent

    data class Progressed(
        val duration: Duration,
    ) : RecordedVideoEvent

    data class Finished(
        val outcome: RecordingOutcome,
    ) : RecordedVideoEvent

    data class Saved(
        val uri: Uri,
        val item: CapturedItem?,
    ) : RecordedVideoEvent
}
