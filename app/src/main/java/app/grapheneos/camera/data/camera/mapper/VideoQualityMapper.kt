package app.grapheneos.camera.data.camera.mapper

import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.core.model.VideoQuality
import javax.inject.Inject

interface VideoQualityMapper {

    fun map(
        camera: BoundCamera,
        requestedQuality: VideoQuality,
    ): VideoQuality
}

internal class VideoQualityMapperImpl @Inject constructor() : VideoQualityMapper {

    override fun map(
        camera: BoundCamera,
        requestedQuality: VideoQuality,
    ): VideoQuality {
        // CameraX lists supported qualities from highest to lowest.
        return when (requestedQuality) {
            VideoQuality.HIGHEST -> camera.videoQualities.firstOrNull() ?: requestedQuality
            else -> requestedQuality
        }
    }
}
