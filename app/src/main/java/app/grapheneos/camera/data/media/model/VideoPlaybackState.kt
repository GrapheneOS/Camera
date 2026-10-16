package app.grapheneos.camera.data.media.model

import kotlin.time.Duration

data class VideoPlaybackState(
    val isPlaying: Boolean = false,
    val duration: Duration? = null,
    val aspectRatio: Float? = null,
    val hasFailed: Boolean = false,
)
