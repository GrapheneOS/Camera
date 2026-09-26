package app.grapheneos.camera.data.camera.model

sealed interface RecordingEvent {

    data object Started : RecordingEvent

    data class Progressed(
        val recordedDurationNanos: Long,
    ) : RecordingEvent

    data class Finalized(
        val outcome: RecordingOutcome,
    ) : RecordingEvent
}
