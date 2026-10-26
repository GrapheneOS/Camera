package app.grapheneos.camera.data.media.model

internal sealed interface VideoOutputChange {

    data class Attached(
        val output: VideoOutput,
    ) : VideoOutputChange

    data class Detached(
        val output: VideoOutput,
    ) : VideoOutputChange
}
