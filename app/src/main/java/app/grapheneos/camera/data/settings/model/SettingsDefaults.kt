package app.grapheneos.camera.data.settings.model

import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import com.google.zxing.BarcodeFormat

object SettingsDefaults {

    val GRID_TYPE = GridType.NONE

    val VIDEO_QUALITY = VideoQuality.HIGHEST

    val ENABLED_BARCODE_FORMATS = setOf(BarcodeFormat.QR_CODE.name)

    val ASPECT_RATIO = AspectRatio.RATIO_4_3

    val FLASH_MODE = FlashMode.OFF

    const val FOCUS_TIMEOUT_SECONDS = 5L

    const val SELF_ILLUMINATION = false

    const val GEO_TAGGING = false

    const val INCLUDE_AUDIO = true

    const val ENABLE_EIS = true

    const val SCAN_ALL_CODES = false

    const val SAVE_IMAGE_AS_PREVIEW = true

    const val SAVE_VIDEO_AS_PREVIEW = true

    const val PHOTO_QUALITY = 95

    const val REMOVE_EXIF_AFTER_CAPTURE = true

    const val GYROSCOPE_SUGGESTIONS = false

    const val CAMERA_SOUNDS = true

    const val ENABLE_ZSL = false

    const val SELECT_HIGHEST_RESOLUTION = false

    const val WAIT_FOR_FOCUS_LOCK = false

    const val SELF_TIMER_DURATION = 0
}
