package app.grapheneos.camera.data.settings.store

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataMigration
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.store.LEGACY_CAPTURE_KEYS
import app.grapheneos.camera.data.core.store.LEGACY_STORAGE_KEYS
import app.grapheneos.camera.data.core.store.legacyCommonPreferences
import app.grapheneos.camera.data.core.store.removeLegacyCommonKeys
import app.grapheneos.camera.data.settings.model.SettingsDefaults

// Settings owns every shared-file key not claimed by capture or storage. The constants and
// fallbacks below describe shipped formats and must remain independent of current UI names.
@Suppress("TooManyFunctions")
internal class SettingsPrefsMigration(
    private val context: Context,
) : DataMigration<SettingsPrefs> {

    override suspend fun shouldMigrate(currentData: SettingsPrefs): Boolean {
        val ownsSomething = legacyCommonPreferences(context).all.keys.any(::owns)

        return ownsSomething || CameraMode.entries.any {
            preferences(SettingsPrefs.storedModeName(it)).all.isNotEmpty()
        }
    }

    override suspend fun migrate(currentData: SettingsPrefs): SettingsPrefs {
        return currentData.copy(
            common = readCommonSettings(
                commons = legacyCommonPreferences(context),
                current = currentData.common,
            ),
            modes = readModeSettings(currentData),
        )
    }

    override suspend fun cleanUp() {
        CameraMode.entries.forEach { mode ->
            context.deleteSharedPreferences(SettingsPrefs.storedModeName(mode))
        }

        removeLegacyCommonKeys(context, ::owns)
    }

    private fun owns(key: String): Boolean {
        return key !in LEGACY_CAPTURE_KEYS && key !in LEGACY_STORAGE_KEYS
    }

    @Suppress("CyclomaticComplexMethod")
    private fun readCommonSettings(
        commons: SharedPreferences,
        current: StoredCameraSettings,
    ): StoredCameraSettings {
        return current.copy(
            aspectRatio = intOrNull(commons, ASPECT_RATIO) ?: current.aspectRatio,
            gridType = readGridType(commons) ?: current.gridType,
            focusTimeoutSeconds = readFocusTimeoutSeconds(commons)
                ?: current.focusTimeoutSeconds,
            selfTimerDurationSeconds = intOrNull(commons, SELF_TIMER_DURATION)
                ?: current.selfTimerDurationSeconds,
            enableCameraSounds = booleanOrNull(commons, CAMERA_SOUNDS)
                ?: current.enableCameraSounds,
            includeAudio = booleanOrNull(commons, INCLUDE_AUDIO) ?: current.includeAudio,
            enableEis = booleanOrNull(commons, ENABLE_EIS) ?: current.enableEis,
            enableZsl = booleanOrNull(commons, ENABLE_ZSL) ?: current.enableZsl,
            waitForFocusLock = booleanOrNull(commons, WAIT_FOR_FOCUS_LOCK)
                ?: current.waitForFocusLock,
            selectHighestResolution = booleanOrNull(commons, SELECT_HIGHEST_RESOLUTION)
                ?: current.selectHighestResolution,
            photoQuality = readPhotoQuality(commons, current.photoQuality),
            removeExifAfterCapture = booleanOrNull(commons, REMOVE_EXIF_AFTER_CAPTURE)
                ?: current.removeExifAfterCapture,
            gyroscopeSuggestions = booleanOrNull(commons, GYROSCOPE_SUGGESTIONS)
                ?: current.gyroscopeSuggestions,
            saveImageAsPreviewed = booleanOrNull(commons, SAVE_IMAGE_AS_PREVIEW)
                ?: current.saveImageAsPreviewed,
            saveVideoAsPreviewed = readSaveVideoAsPreviewed(
                commons = commons,
                current = current.saveVideoAsPreviewed,
            ),
            scanAllCodes = booleanOrNull(commons, SCAN_ALL_CODES) ?: current.scanAllCodes,
            enabledBarcodeFormats = readEnabledBarcodeFormats(commons)
                ?: current.enabledBarcodeFormats,
        )
    }

    private fun readGridType(commons: SharedPreferences): StoredGridType? {
        val ordinal = intOrNull(commons, GRID) ?: return null

        return LEGACY_GRID_TYPES.getOrNull(ordinal) ?: StoredGridType.NONE
    }

    private fun readFocusTimeoutSeconds(commons: SharedPreferences): Long? {
        return when (val label = commons.getString(FOCUS_TIMEOUT, null)) {
            null -> null
            FOCUS_TIMEOUT_OFF -> 0L
            else -> label.removeSuffix("s").toLongOrNull()
                ?: SettingsDefaults.FOCUS_TIMEOUT_SECONDS
        }
    }

    // Older versions stored either a quality toggle or an invalid zero from the seek bar.
    private fun readPhotoQuality(commons: SharedPreferences, current: Int?): Int? {
        val stored = intOrNull(commons, PHOTO_QUALITY)

        return when {
            stored != null -> stored.takeIf { it > 0 } ?: SettingsDefaults.PHOTO_QUALITY
            booleanOrNull(commons, EMPHASIS_ON_QUALITY) == true -> MAX_PHOTO_QUALITY
            commons.contains(EMPHASIS_ON_QUALITY) -> SettingsDefaults.PHOTO_QUALITY
            else -> current
        }
    }

    // Installs predating this setting recorded without preview mirroring.
    private fun readSaveVideoAsPreviewed(
        commons: SharedPreferences,
        current: Boolean?,
    ): Boolean? {
        val predatesTheSetting = commons.contains(SAVE_IMAGE_AS_PREVIEW) &&
            !commons.contains(SAVE_VIDEO_AS_PREVIEW)

        return when {
            predatesTheSetting -> false
            else -> booleanOrNull(commons, SAVE_VIDEO_AS_PREVIEW) ?: current
        }
    }

    // The default format's key marked the legacy set as explicitly configured.
    private fun readEnabledBarcodeFormats(commons: SharedPreferences): Set<String>? {
        if (!commons.contains(DEFAULT_SCAN_KEY)) {
            return null
        }

        return commons.all.keys
            .filter { it.startsWith(SCAN_PREFIX) && it != SCAN_ALL_CODES }
            .filterTo(mutableSetOf()) { commons.getBoolean(it, false) }
            .mapTo(mutableSetOf()) { it.removePrefix(SCAN_PREFIX) }
    }

    private fun readModeSettings(current: SettingsPrefs): Map<String, StoredModeSettings> {
        var migrated = current

        CameraMode.entries.forEach { mode ->
            val modePreferences = preferences(SettingsPrefs.storedModeName(mode))

            if (modePreferences.all.isNotEmpty()) {
                migrated = migrated.withMode(
                    mode = mode,
                    settings = readMode(
                        modePreferences = modePreferences,
                        current = migrated.mode(mode),
                    ),
                )
            }
        }

        return migrated.modes
    }

    private fun readMode(
        modePreferences: SharedPreferences,
        current: StoredModeSettings,
    ): StoredModeSettings {
        return current.copy(
            flashMode = intOrNull(modePreferences, FLASH_MODE) ?: current.flashMode,
            geoTagging = booleanOrNull(modePreferences, GEO_TAGGING) ?: current.geoTagging,
            selfIllumination = booleanOrNull(modePreferences, SELF_ILLUMINATION)
                ?: current.selfIllumination,
            videoQualityFront = readVideoQuality(
                modePreferences = modePreferences,
                key = VIDEO_QUALITY_FRONT,
                current = current.videoQualityFront,
            ),
            videoQualityBack = readVideoQuality(
                modePreferences = modePreferences,
                key = VIDEO_QUALITY_BACK,
                current = current.videoQualityBack,
            ),
        )
    }

    // The shipped placeholder label fell through to SD, so preserve that migration behavior.
    private fun readVideoQuality(
        modePreferences: SharedPreferences,
        key: String,
        current: StoredVideoQuality,
    ): StoredVideoQuality {
        val title = modePreferences.getString(key, null)
            ?: return current

        return LEGACY_VIDEO_QUALITIES[title] ?: UNRECOGNISED_VIDEO_QUALITY
    }

    private fun intOrNull(preferences: SharedPreferences, key: String): Int? {
        return when {
            preferences.contains(key) -> preferences.getInt(key, 0)
            else -> null
        }
    }

    private fun booleanOrNull(preferences: SharedPreferences, key: String): Boolean? {
        return when {
            preferences.contains(key) -> preferences.getBoolean(key, false)
            else -> null
        }
    }

    private fun preferences(name: String): SharedPreferences {
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    private companion object {
        const val ASPECT_RATIO = "aspect_ratio"
        const val CAMERA_SOUNDS = "camera_sounds"
        const val EMPHASIS_ON_QUALITY = "emphasis_on_quality"
        const val ENABLE_EIS = "enable_eis"
        const val ENABLE_ZSL = "enable_zsl"
        const val FLASH_MODE = "flash_mode"
        const val FOCUS_TIMEOUT = "focus_timeout"
        const val GEO_TAGGING = "geo_tagging"
        const val GRID = "grid"
        const val GYROSCOPE_SUGGESTIONS = "gyroscope_suggestions"
        const val INCLUDE_AUDIO = "include_audio"
        const val PHOTO_QUALITY = "photo_quality"
        const val REMOVE_EXIF_AFTER_CAPTURE = "remove_exif_after_capture"
        const val SAVE_IMAGE_AS_PREVIEW = "save_image_as_preview"
        const val SAVE_VIDEO_AS_PREVIEW = "save_video_as_preview"
        const val SCAN_ALL_CODES = "scan_all_codes"
        const val SCAN_PREFIX = "scan_"
        const val DEFAULT_SCAN_KEY = "scan_QR_CODE"
        const val SELECT_HIGHEST_RESOLUTION = "select_highest_resolution"
        const val SELF_ILLUMINATION = "self_illumination"
        const val SELF_TIMER_DURATION = "self_timer_duration"
        const val WAIT_FOR_FOCUS_LOCK = "wait_for_focus_lock"
        const val VIDEO_QUALITY_FRONT = "video_quality_FRONT"
        const val VIDEO_QUALITY_BACK = "video_quality_BACK"

        const val FOCUS_TIMEOUT_OFF = "Off"
        const val MAX_PHOTO_QUALITY = 100
        val UNRECOGNISED_VIDEO_QUALITY = StoredVideoQuality.SD

        // This order is the shipped ordinal wire format.
        val LEGACY_GRID_TYPES = listOf(
            StoredGridType.NONE,
            StoredGridType.THREE_BY_THREE,
            StoredGridType.FOUR_BY_FOUR,
            StoredGridType.GOLDEN_RATIO,
        )

        val LEGACY_VIDEO_QUALITIES = mapOf(
            "2160p (UHD)" to StoredVideoQuality.UHD,
            "1080p (FHD)" to StoredVideoQuality.FHD,
            "720p (HD)" to StoredVideoQuality.HD,
            "480p (SD)" to StoredVideoQuality.SD,
        )
    }
}
