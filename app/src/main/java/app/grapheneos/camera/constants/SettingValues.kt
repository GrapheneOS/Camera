package app.grapheneos.camera.constants

import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageCapture
import androidx.camera.video.QualitySelector

object SettingValues {

    object Key {
        const val SELF_ILLUMINATION = "self_illumination"
        const val GEO_TAGGING = "geo_tagging"
        const val FLASH_MODE = "flash_mode"
        const val GRID = "grid"
        const val EMPHASIS_ON_QUALITY = "emphasis_on_quality"
        const val FOCUS_TIMEOUT = "focus_timeout"
        const val CAMERA_SOUNDS = "camera_sounds"
        const val VIDEO_QUALITY = "video_quality"
    }

    object Default {

        val GRID_TYPE = GridType.NONE
        const val GRID_TYPE_INDEX = 0

        const val ASPECT_RATIO = AspectRatio.RATIO_4_3

        const val VIDEO_QUALITY = QualitySelector.QUALITY_FHD

        const val SELF_ILLUMINATION = false

        const val GEO_TAGGING = false

        const val FLASH_MODE = ImageCapture.FLASH_MODE_OFF

        const val EMPHASIS_ON_QUALITY = true

        const val FOCUS_TIMEOUT = "5s"

        const val CAMERA_SOUNDS = true
    }

}