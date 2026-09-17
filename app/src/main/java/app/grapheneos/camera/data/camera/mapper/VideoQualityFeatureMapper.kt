package app.grapheneos.camera.data.camera.mapper

import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.video.GroupableFeatures
import app.grapheneos.camera.data.core.model.VideoQuality
import javax.inject.Inject

interface VideoQualityFeatureMapper {

    fun map(quality: VideoQuality): GroupableFeature?

    fun map(feature: GroupableFeature): VideoQuality?
}

internal class VideoQualityFeatureMapperImpl @Inject constructor() : VideoQualityFeatureMapper {

    override fun map(quality: VideoQuality): GroupableFeature? {
        return when (quality) {
            VideoQuality.UHD -> GroupableFeatures.UHD_RECORDING
            VideoQuality.FHD -> GroupableFeatures.FHD_RECORDING
            VideoQuality.HD -> GroupableFeatures.HD_RECORDING
            VideoQuality.SD -> GroupableFeatures.SD_RECORDING
            VideoQuality.HIGHEST -> null
        }
    }

    override fun map(feature: GroupableFeature): VideoQuality? {
        return when (feature) {
            GroupableFeatures.UHD_RECORDING -> VideoQuality.UHD
            GroupableFeatures.FHD_RECORDING -> VideoQuality.FHD
            GroupableFeatures.HD_RECORDING -> VideoQuality.HD
            GroupableFeatures.SD_RECORDING -> VideoQuality.SD
            else -> null
        }
    }
}
