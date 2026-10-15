package app.grapheneos.camera.data.camera.model

sealed interface InVideoSnapshotSupport {

    data object Supported : InVideoSnapshotSupport

    enum class Unsupported(
        val reason: String,
    ) : InVideoSnapshotSupport {
        STREAM_COMBINATION(
            "Video, photo and preview can't be bound together on this camera",
        ),
        SELECTED_VIDEO_SETTINGS(
            "This camera can't record with the selected video quality and HDR with in-video " +
                "snapshots enabled",
        ),
    }
}
