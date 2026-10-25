package app.grapheneos.camera.data.media.model

import kotlin.time.Duration

internal data class VideoPlaybackState(
    val isPlaying: Boolean = false,
    val duration: Duration? = null,
    val aspectRatio: Float? = null,
    val hasRenderedFrame: Boolean = false,
    val renderedOutputId: Int? = null,
    val hasFailed: Boolean = false,
)
