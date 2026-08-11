package app.grapheneos.camera.data.settings.repository

/** The preference keys the settings are stored under. Renaming one resets that setting. */
internal object SettingsKeys {

    const val SELF_ILLUMINATION = "self_illumination"
    const val GEO_TAGGING = "geo_tagging"
    const val FLASH_MODE = "flash_mode"
    const val GRID = "grid"

    /** Obsolete, split into [WAIT_FOR_FOCUS_LOCK] and [PHOTO_QUALITY]. */
    const val EMPHASIS_ON_QUALITY = "emphasis_on_quality"

    const val FOCUS_TIMEOUT = "focus_timeout"
    const val VIDEO_QUALITY = "video_quality"
    const val ASPECT_RATIO = "aspect_ratio"
    const val INCLUDE_AUDIO = "include_audio"
    const val ENABLE_EIS = "enable_eis"
    const val SCAN = "scan"
    const val SCAN_ALL_CODES = "scan_all_codes"
    const val SAVE_IMAGE_AS_PREVIEW = "save_image_as_preview"
    const val SAVE_VIDEO_AS_PREVIEW = "save_video_as_preview"

    const val PHOTO_QUALITY = "photo_quality"

    const val REMOVE_EXIF_AFTER_CAPTURE = "remove_exif_after_capture"

    const val GYROSCOPE_SUGGESTIONS = "gyroscope_suggestions"

    const val CAMERA_SOUNDS = "camera_sounds"

    const val ENABLE_ZSL = "enable_zsl"

    const val SELECT_HIGHEST_RESOLUTION = "select_highest_resolution"

    const val WAIT_FOR_FOCUS_LOCK = "wait_for_focus_lock"

    const val SELF_TIMER_DURATION = "self_timer_duration"

    fun scanKey(formatName: String): String {
        return "${SCAN}_$formatName"
    }
}
