package app.grapheneos.camera.data.camera.model

import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.data.core.model.VideoQuality

data class VideoBindSettings(
    val quality: VideoQuality,
    val frameRate: VideoFrameRate,
    val stabilization: Boolean,
    val hdr: Boolean,
    val mirrorOnFrontCamera: Boolean,
)
