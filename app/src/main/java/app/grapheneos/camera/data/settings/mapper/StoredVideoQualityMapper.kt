package app.grapheneos.camera.data.settings.mapper

import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.store.StoredVideoQuality
import javax.inject.Inject

internal interface StoredVideoQualityMapper {

    fun map(quality: VideoQuality): StoredVideoQuality
}

internal class StoredVideoQualityMapperImpl @Inject constructor() : StoredVideoQualityMapper {

    override fun map(quality: VideoQuality): StoredVideoQuality {
        return when (quality) {
            VideoQuality.HIGHEST -> StoredVideoQuality.DEVICE_CHOICE
            VideoQuality.UHD -> StoredVideoQuality.UHD
            VideoQuality.FHD -> StoredVideoQuality.FHD
            VideoQuality.HD -> StoredVideoQuality.HD
            VideoQuality.SD -> StoredVideoQuality.SD
        }
    }
}
