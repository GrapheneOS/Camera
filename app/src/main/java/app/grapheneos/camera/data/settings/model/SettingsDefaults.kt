package app.grapheneos.camera.data.settings.model

import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CaptureMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import com.google.zxing.BarcodeFormat

object SettingsDefaults {

    val ASPECT_RATIO = AspectRatio.RATIO_4_3

    val GRID_TYPE = GridType.NONE

    val VOLUME_KEY_ACTION = VolumeKeyAction.SHUTTER

    val CAPTURE_MODE = CaptureMode.SPEED

    val ENABLED_BARCODE_FORMATS = setOf(BarcodeFormat.QR_CODE.name)

    val FLASH_MODE = FlashMode.OFF

    val VIDEO_QUALITY = VideoQuality.HIGHEST

    const val FOCUS_TIMEOUT_SECONDS = 5L

    const val SELF_TIMER_DURATION = 0

    const val TIMER_LIGHT = false

    const val CAMERA_SOUNDS = true

    const val INCLUDE_AUDIO = true

    const val ENABLE_EIS = true

    const val HDR_VIDEO = false

    const val SELECT_HIGHEST_RESOLUTION = false

    const val ULTRA_HDR = false

    const val PHOTO_QUALITY = 95

    const val REMOVE_EXIF_AFTER_CAPTURE = true

    const val GYROSCOPE_SUGGESTIONS = false

    const val KEEP_SELFIES_AS_SEEN = true

    const val SCAN_ALL_CODES = false

    const val GEO_TAGGING = false

    const val SELF_ILLUMINATION = false
}
