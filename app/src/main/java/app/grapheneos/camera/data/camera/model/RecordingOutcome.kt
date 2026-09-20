package app.grapheneos.camera.data.camera.model

sealed interface RecordingOutcome {

    data object Saved : RecordingOutcome

    data object NothingPlayableWritten : RecordingOutcome

    data class Failed(
        val errorCode: Int,
    ) : RecordingOutcome

    data class Interrupted(
        val errorCode: Int,
        val hasContent: Boolean,
    ) : RecordingOutcome
}
