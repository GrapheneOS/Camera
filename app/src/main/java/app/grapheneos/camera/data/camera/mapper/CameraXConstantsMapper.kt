package app.grapheneos.camera.data.camera.mapper

import androidx.camera.core.AspectRatio as CameraXAspectRatio
import androidx.camera.core.ImageCapture
import androidx.camera.extensions.ExtensionMode as CameraXExtensionMode
import androidx.camera.video.Quality
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.ExtensionMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import javax.inject.Inject

interface CameraXConstantsMapper {

    fun map(flashMode: FlashMode): Int

    fun map(aspectRatio: AspectRatio): Int

    fun map(extensionMode: ExtensionMode): Int

    fun map(videoQuality: VideoQuality): Quality

    fun map(quality: Quality): VideoQuality?
}

internal class CameraXConstantsMapperImpl @Inject constructor() : CameraXConstantsMapper {

    override fun map(flashMode: FlashMode): Int {
        return when (flashMode) {
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        }
    }

    override fun map(aspectRatio: AspectRatio): Int {
        return when (aspectRatio) {
            AspectRatio.RATIO_4_3 -> CameraXAspectRatio.RATIO_4_3
            AspectRatio.RATIO_16_9 -> CameraXAspectRatio.RATIO_16_9
        }
    }

    override fun map(extensionMode: ExtensionMode): Int {
        return when (extensionMode) {
            ExtensionMode.AUTO -> CameraXExtensionMode.AUTO
            ExtensionMode.FACE_RETOUCH -> CameraXExtensionMode.FACE_RETOUCH
            ExtensionMode.BOKEH -> CameraXExtensionMode.BOKEH
            ExtensionMode.NIGHT -> CameraXExtensionMode.NIGHT
            ExtensionMode.HDR -> CameraXExtensionMode.HDR
        }
    }

    override fun map(videoQuality: VideoQuality): Quality {
        return when (videoQuality) {
            VideoQuality.HIGHEST -> Quality.HIGHEST
            VideoQuality.UHD -> Quality.UHD
            VideoQuality.FHD -> Quality.FHD
            VideoQuality.HD -> Quality.HD
            VideoQuality.SD -> Quality.SD
        }
    }

    override fun map(quality: Quality): VideoQuality? {
        return when (quality) {
            Quality.HIGHEST -> VideoQuality.HIGHEST
            Quality.UHD -> VideoQuality.UHD
            Quality.FHD -> VideoQuality.FHD
            Quality.HD -> VideoQuality.HD
            Quality.SD -> VideoQuality.SD
            else -> null
        }
    }
}
