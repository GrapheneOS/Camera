package app.grapheneos.camera.domain.camera.model

sealed interface InVideoSnapshotSupport {

    data object Supported : InVideoSnapshotSupport

    enum class Unsupported(
        val reason: String,
    ) : InVideoSnapshotSupport {
        STREAM_COMBINATION(
            "Video, photo and preview can't be bound together on this camera",
        ),
        SELECTED_VIDEO_QUALITY(
            "This camera can't record at the selected video quality with in-video " +
                "snapshots enabled",
        ),
    }
}
