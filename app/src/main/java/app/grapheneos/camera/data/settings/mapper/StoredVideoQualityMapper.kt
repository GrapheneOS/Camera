package app.grapheneos.camera.data.settings.mapper

import androidx.camera.video.Quality
import app.grapheneos.camera.data.settings.store.StoredVideoQuality
import javax.inject.Inject

internal interface StoredVideoQualityMapper {

    fun map(quality: Quality): StoredVideoQuality
}

internal class StoredVideoQualityMapperImpl @Inject constructor() : StoredVideoQualityMapper {

    override fun map(quality: Quality): StoredVideoQuality {
        return when (quality) {
            Quality.UHD -> StoredVideoQuality.UHD
            Quality.FHD -> StoredVideoQuality.FHD
            Quality.HD -> StoredVideoQuality.HD
            Quality.SD -> StoredVideoQuality.SD
            else -> StoredVideoQuality.DEVICE_CHOICE
        }
    }
}
