package app.grapheneos.camera.data.camera.mapper

import androidx.camera.video.VideoRecordEvent
import app.grapheneos.camera.data.camera.model.RecordingEvent
import app.grapheneos.camera.data.camera.model.RecordingOutcome
import javax.inject.Inject

interface RecordingEventMapper {
    fun map(event: VideoRecordEvent): RecordingEvent?
}

internal class RecordingEventMapperImpl @Inject constructor() : RecordingEventMapper {

    override fun map(event: VideoRecordEvent): RecordingEvent? {
        return when (event) {
            is VideoRecordEvent.Start -> {
                RecordingEvent.Started
            }

            is VideoRecordEvent.Status -> {
                RecordingEvent.Progressed(event.recordingStats.recordedDurationNanos)
            }

            is VideoRecordEvent.Finalize -> {
                RecordingEvent.Finalized(outcomeOf(event))
            }

            else -> null
        }
    }

    private fun outcomeOf(event: VideoRecordEvent.Finalize): RecordingOutcome {
        return when {
            !event.hasError() -> RecordingOutcome.Saved

            event.error == VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> {
                RecordingOutcome.NothingPlayableWritten
            }

            event.error in UNRECOVERABLE_ERRORS -> {
                RecordingOutcome.Failed(errorCode = event.error)
            }

            else -> RecordingOutcome.Interrupted(
                errorCode = event.error,
                hasContent = event.recordingStats.numBytesRecorded != 0L,
            )
        }
    }

    private companion object {
        private val UNRECOVERABLE_ERRORS = setOf(
            VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED,
            VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR,
            VideoRecordEvent.Finalize.ERROR_UNKNOWN,
        )
    }
}
