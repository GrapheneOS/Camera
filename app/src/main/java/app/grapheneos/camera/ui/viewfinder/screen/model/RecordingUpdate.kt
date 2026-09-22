package app.grapheneos.camera.ui.viewfinder.screen.model

import android.net.Uri
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.data.camera.model.RecordingOutcome
import kotlin.time.Duration

sealed interface RecordingUpdate {

    data object OutputUnavailable : RecordingUpdate

    data object LocationUnavailable : RecordingUpdate

    data object ReadyToStart : RecordingUpdate

    data object Abandoned : RecordingUpdate

    data object Started : RecordingUpdate

    data object SaveFailed : RecordingUpdate

    data class Progressed(
        val duration: Duration,
    ) : RecordingUpdate

    data class Finished(
        val outcome: RecordingOutcome,
    ) : RecordingUpdate

    data class Saved(
        val uri: Uri,
        val item: CapturedItem?,
    ) : RecordingUpdate
}
