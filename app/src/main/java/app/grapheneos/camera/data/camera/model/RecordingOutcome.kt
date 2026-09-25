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

    fun keepsContent(): Boolean {
        return when (this) {
            is Saved -> true
            is Interrupted -> hasContent
            is NothingPlayableWritten -> false
            is Failed -> false
        }
    }
}
