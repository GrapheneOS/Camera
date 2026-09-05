package app.grapheneos.camera.domain.camera.mapper

import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.video.GroupableFeatures
import androidx.camera.video.Quality
import javax.inject.Inject

interface VideoQualityFeatureMapper {

    fun map(quality: Quality): GroupableFeature?

    fun map(feature: GroupableFeature): Quality?
}

internal class VideoQualityFeatureMapperImpl @Inject constructor() : VideoQualityFeatureMapper {

    override fun map(quality: Quality): GroupableFeature? {
        return when (quality) {
            Quality.UHD -> GroupableFeatures.UHD_RECORDING
            Quality.FHD -> GroupableFeatures.FHD_RECORDING
            Quality.HD -> GroupableFeatures.HD_RECORDING
            Quality.SD -> GroupableFeatures.SD_RECORDING
            else -> null
        }
    }

    override fun map(feature: GroupableFeature): Quality? {
        return when (feature) {
            GroupableFeatures.UHD_RECORDING -> Quality.UHD
            GroupableFeatures.FHD_RECORDING -> Quality.FHD
            GroupableFeatures.HD_RECORDING -> Quality.HD
            GroupableFeatures.SD_RECORDING -> Quality.SD
            else -> null
        }
    }
}
