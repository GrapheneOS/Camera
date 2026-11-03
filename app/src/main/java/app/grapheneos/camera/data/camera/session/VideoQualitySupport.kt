package app.grapheneos.camera.data.camera.session

import android.util.Log
import androidx.camera.core.CameraInfo
import androidx.camera.core.DynamicRange
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.video.Recorder
import app.grapheneos.camera.data.camera.mapper.CameraXConstantsMapper
import app.grapheneos.camera.data.camera.mapper.VideoQualityFeatureMapper
import app.grapheneos.camera.data.core.model.VideoQuality
import javax.inject.Inject

interface VideoQualitySupport {

    /** Highest first, as CameraX lists them. */
    fun supported(cameraInfo: CameraInfo): List<VideoQuality>

    fun groupableFeature(
        quality: VideoQuality,
        cameraInfo: CameraInfo?,
    ): GroupableFeature?
}

internal class VideoQualitySupportImpl @Inject constructor(
    private val videoQualityFeatureMapper: VideoQualityFeatureMapper,
    private val cameraXConstantsMapper: CameraXConstantsMapper,
) : VideoQualitySupport {

    override fun supported(cameraInfo: CameraInfo): List<VideoQuality> {
        return Recorder.getVideoCapabilities(cameraInfo)
            .getSupportedQualities(DynamicRange.SDR)
            .mapNotNull { cameraXConstantsMapper.map(it) }
    }

    // Maps the user-chosen video quality to the equivalent groupable feature, for use when a
    // feature group is passed to SessionConfig (see bind). VideoQuality.HIGHEST has no
    // groupable equivalent and is resolved to the highest quality the current camera supports,
    // mirroring what QualitySelector.from(Quality.HIGHEST) would have selected.
    override fun groupableFeature(
        quality: VideoQuality,
        cameraInfo: CameraInfo?,
    ): GroupableFeature? {
        val resolved = when (quality) {
            VideoQuality.HIGHEST -> cameraInfo?.let { supported(it).firstOrNull() }
            else -> quality
        }
        val feature = resolved?.let(videoQualityFeatureMapper::map)

        if (resolved != null && feature == null) {
            // Not fatal: bind() then requests no quality feature at all and the quality is
            // left to Recorder's default selector. Worth a log because it means the user's
            // explicit choice is silently not being asked for.
            Log.w(TAG, "No groupable feature equivalent for video quality $resolved")
        }

        return feature
    }

    private companion object {
        const val TAG = "VideoQualitySupport"
    }
}
