package app.grapheneos.camera.data.settings.repository

import android.content.SharedPreferences
import androidx.camera.video.Quality
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.store.STORAGE_LOCATION_KEY
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.data.settings.model.focusTimeoutLabel
import app.grapheneos.camera.data.settings.model.focusTimeoutSecondsFromLabel
import app.grapheneos.camera.data.settings.model.storableVideoQualityTitle
import app.grapheneos.camera.data.settings.model.videoQualityFromTitle
import app.grapheneos.camera.di.core.SessionPreferences
import app.grapheneos.camera.util.edit
import app.grapheneos.camera.util.unitFlow
import com.google.zxing.BarcodeFormat
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface SettingsRepository {

    val settings: StateFlow<CameraSettings>

    fun refresh()

    fun setAspectRatio(value: Int): Flow<Unit>

    fun setGridType(value: GridType): Flow<Unit>

    fun setFocusTimeoutSeconds(value: Long): Flow<Unit>

    fun setSelfTimerDurationSeconds(value: Int): Flow<Unit>

    fun setEnableCameraSounds(value: Boolean): Flow<Unit>

    fun setIncludeAudio(value: Boolean): Flow<Unit>

    fun setEnableEis(value: Boolean): Flow<Unit>

    fun setEnableZsl(value: Boolean): Flow<Unit>

    fun setWaitForFocusLock(value: Boolean): Flow<Unit>

    fun setSelectHighestResolution(value: Boolean): Flow<Unit>

    fun setPhotoQuality(value: Int): Flow<Unit>

    fun setRemoveExifAfterCapture(value: Boolean): Flow<Unit>

    fun setGyroscopeSuggestions(value: Boolean): Flow<Unit>

    fun setSaveImageAsPreviewed(value: Boolean): Flow<Unit>

    fun setSaveVideoAsPreviewed(value: Boolean): Flow<Unit>

    fun setScanAllCodes(value: Boolean): Flow<Unit>

    fun setStorageLocation(value: String): Flow<Unit>

    fun isBarcodeFormatEnabled(formatName: String): Boolean

    fun setBarcodeFormatEnabled(formatName: String, enabled: Boolean): Flow<Unit>

    val modeSettings: StateFlow<ModeSettings>

    fun reslotMode(mode: CameraMode, isFrontFacing: Boolean)

    fun setFlashMode(value: Int): Flow<Unit>

    fun setGeoTagging(value: Boolean): Flow<Unit>

    fun setSelfIllumination(value: Boolean): Flow<Unit>

    fun setVideoQuality(value: Quality): Flow<Unit>
}

internal class SettingsRepositoryImpl @Inject constructor(
    @SessionPreferences private val commons: SharedPreferences,
    private val modePreferences: Map<CameraMode, @JvmSuppressWildcards Lazy<SharedPreferences>>,
) : SettingsRepository {

    private val mutableSettings = MutableStateFlow(
        run {
            seedCommonDefaults()
            readCommonSettings()
        },
    )

    override val settings: StateFlow<CameraSettings> = mutableSettings.asStateFlow()

    override fun refresh() {
        mutableSettings.value = readCommonSettings()
    }

    private val mutableModeSettings = MutableStateFlow(ModeSettings())

    private var slotted: SlottedMode? = null

    override fun setAspectRatio(value: Int): Flow<Unit> {
        return write({ it.copy(aspectRatio = value) }) {
            commons.edit {
                putInt(SettingsKeys.ASPECT_RATIO, value)
            }
        }
    }

    override fun setGridType(value: GridType): Flow<Unit> {
        return write({ it.copy(gridType = value) }) {
            commons.edit {
                putInt(SettingsKeys.GRID, value.ordinal)
            }
        }
    }

    override fun setFocusTimeoutSeconds(value: Long): Flow<Unit> {
        return write({ it.copy(focusTimeoutSeconds = value) }) {
            commons.edit {
                putString(SettingsKeys.FOCUS_TIMEOUT, focusTimeoutLabel(value))
            }
        }
    }

    override fun setSelfTimerDurationSeconds(value: Int): Flow<Unit> {
        return write({ it.copy(selfTimerDurationSeconds = value) }) {
            commons.edit {
                putInt(SettingsKeys.SELF_TIMER_DURATION, value)
            }
        }
    }

    override fun setEnableCameraSounds(value: Boolean): Flow<Unit> {
        return write({ it.copy(enableCameraSounds = value) }) {
            commons.edit {
                putBoolean(SettingsKeys.CAMERA_SOUNDS, value)
            }
        }
    }

    override fun setIncludeAudio(value: Boolean): Flow<Unit> {
        return write({ it.copy(includeAudio = value) }) {
            commons.edit {
                putBoolean(SettingsKeys.INCLUDE_AUDIO, value)
            }
        }
    }

    override fun setEnableEis(value: Boolean): Flow<Unit> {
        return write({ it.copy(enableEis = value) }) {
            commons.edit {
                putBoolean(SettingsKeys.ENABLE_EIS, value)
            }
        }
    }

    override fun setEnableZsl(value: Boolean): Flow<Unit> {
        return write({ it.copy(enableZsl = value) }) {
            commons.edit {
                putBoolean(SettingsKeys.ENABLE_ZSL, value)
            }
        }
    }

    override fun setWaitForFocusLock(value: Boolean): Flow<Unit> {
        return write({ it.copy(waitForFocusLock = value) }) {
            commons.edit {
                putBoolean(SettingsKeys.WAIT_FOR_FOCUS_LOCK, value)
            }
        }
    }

    override fun setSelectHighestResolution(value: Boolean): Flow<Unit> {
        return write({ it.copy(selectHighestResolution = value) }) {
            commons.edit {
                putBoolean(SettingsKeys.SELECT_HIGHEST_RESOLUTION, value)
            }
        }
    }

    override fun setPhotoQuality(value: Int): Flow<Unit> {
        return write({ it.copy(photoQuality = value) }) {
            storePhotoQuality(value)
        }
    }

    override fun setRemoveExifAfterCapture(value: Boolean): Flow<Unit> {
        return write({ it.copy(removeExifAfterCapture = value) }) {
            commons.edit {
                putBoolean(SettingsKeys.REMOVE_EXIF_AFTER_CAPTURE, value)
            }
        }
    }

    override fun setGyroscopeSuggestions(value: Boolean): Flow<Unit> {
        return write({ it.copy(gyroscopeSuggestions = value) }) {
            commons.edit {
                putBoolean(SettingsKeys.GYROSCOPE_SUGGESTIONS, value)
            }
        }
    }

    override fun setSaveImageAsPreviewed(value: Boolean): Flow<Unit> {
        return write({ it.copy(saveImageAsPreviewed = value) }) {
            commons.edit {
                putBoolean(SettingsKeys.SAVE_IMAGE_AS_PREVIEW, value)
            }
        }
    }

    override fun setSaveVideoAsPreviewed(value: Boolean): Flow<Unit> {
        return write({ it.copy(saveVideoAsPreviewed = value) }) {
            commons.edit {
                putBoolean(SettingsKeys.SAVE_VIDEO_AS_PREVIEW, value)
            }
        }
    }

    override fun setScanAllCodes(value: Boolean): Flow<Unit> {
        return write({ it.copy(scanAllCodes = value) }) {
            commons.edit {
                putBoolean(SettingsKeys.SCAN_ALL_CODES, value)
            }
        }
    }

    override fun setStorageLocation(value: String): Flow<Unit> {
        return write({ it.copy(storageLocation = value) }) {
            commons.edit {
                putString(STORAGE_LOCATION_KEY, value)
            }
        }
    }

    override fun isBarcodeFormatEnabled(formatName: String): Boolean {
        return commons.getBoolean(SettingsKeys.scanKey(formatName), false)
    }

    override fun setBarcodeFormatEnabled(formatName: String, enabled: Boolean): Flow<Unit> {
        return unitFlow {
            commons.edit {
                putBoolean(SettingsKeys.scanKey(formatName), enabled)
            }
        }
    }

    override val modeSettings: StateFlow<ModeSettings> = mutableModeSettings.asStateFlow()

    override fun reslotMode(mode: CameraMode, isFrontFacing: Boolean) {
        seedModeDefaults(mode = mode, isFrontFacing = isFrontFacing)

        slotted = SlottedMode(mode = mode, isFrontFacing = isFrontFacing)
        mutableModeSettings.value = readModeSettings(mode = mode, isFrontFacing = isFrontFacing)
    }

    override fun setFlashMode(value: Int): Flow<Unit> {
        return writeMode({ it.copy(flashMode = value) }) { mode ->
            preferencesFor(mode.mode).edit {
                putInt(SettingsKeys.FLASH_MODE, value)
            }
        }
    }

    override fun setGeoTagging(value: Boolean): Flow<Unit> {
        return writeMode({ it.copy(geoTagging = value) }) { mode ->
            preferencesFor(mode.mode).edit {
                putBoolean(SettingsKeys.GEO_TAGGING, value)
            }
        }
    }

    override fun setSelfIllumination(value: Boolean): Flow<Unit> {
        return writeMode({ it.copy(selfIllumination = value) }) { mode ->
            preferencesFor(mode.mode).edit {
                putBoolean(SettingsKeys.SELF_ILLUMINATION, value)
            }
        }
    }

    override fun setVideoQuality(value: Quality): Flow<Unit> {
        return writeMode({ it.copy(videoQuality = value) }) { mode ->
            val qualityKey = videoQualityKey(mode.isFrontFacing)

            preferencesFor(mode.mode).edit {
                when (val title = storableVideoQualityTitle(value)) {
                    null -> remove(qualityKey)
                    else -> putString(qualityKey, title)
                }
            }
        }
    }

    private fun write(
        update: (CameraSettings) -> CameraSettings,
        persist: () -> Unit,
    ): Flow<Unit> {
        return unitFlow {
            mutableSettings.update(update)
            persist()
        }
    }

    private fun writeMode(
        update: (ModeSettings) -> ModeSettings,
        persist: (SlottedMode) -> Unit,
    ): Flow<Unit> {
        return unitFlow {
            slotted?.let { mode ->
                mutableModeSettings.update(update)
                persist(mode)
            }
        }
    }

    private fun readCommonSettings(): CameraSettings {
        val gridOrdinal = commons.getInt(
            SettingsKeys.GRID,
            SettingsDefaults.GRID_TYPE.ordinal,
        )

        return CameraSettings(
            aspectRatio = commons.getInt(
                SettingsKeys.ASPECT_RATIO,
                SettingsDefaults.ASPECT_RATIO,
            ),
            gridType = GridType.entries.getOrElse(gridOrdinal) { SettingsDefaults.GRID_TYPE },
            focusTimeoutSeconds = focusTimeoutSecondsFromLabel(
                commons.getString(
                    SettingsKeys.FOCUS_TIMEOUT,
                    focusTimeoutLabel(SettingsDefaults.FOCUS_TIMEOUT_SECONDS),
                ),
            ),
            selfTimerDurationSeconds = commons.getInt(
                SettingsKeys.SELF_TIMER_DURATION,
                SettingsDefaults.SELF_TIMER_DURATION,
            ),
            photoQuality = commons.getInt(
                SettingsKeys.PHOTO_QUALITY,
                SettingsDefaults.PHOTO_QUALITY,
            ),
            storageLocation = commons.getString(
                STORAGE_LOCATION_KEY,
                SettingsDefaults.STORAGE_LOCATION,
            ).orEmpty(),
        ).withStoredToggles()
    }

    private fun CameraSettings.withStoredToggles(): CameraSettings {
        return copy(
            enableCameraSounds = commons.getBoolean(
                SettingsKeys.CAMERA_SOUNDS,
                SettingsDefaults.CAMERA_SOUNDS,
            ),
            includeAudio = commons.getBoolean(
                SettingsKeys.INCLUDE_AUDIO,
                SettingsDefaults.INCLUDE_AUDIO,
            ),
            enableEis = commons.getBoolean(
                SettingsKeys.ENABLE_EIS,
                SettingsDefaults.ENABLE_EIS,
            ),
            enableZsl = commons.getBoolean(
                SettingsKeys.ENABLE_ZSL,
                SettingsDefaults.ENABLE_ZSL,
            ),
            waitForFocusLock = commons.getBoolean(
                SettingsKeys.WAIT_FOR_FOCUS_LOCK,
                SettingsDefaults.WAIT_FOR_FOCUS_LOCK,
            ),
            selectHighestResolution = commons.getBoolean(
                SettingsKeys.SELECT_HIGHEST_RESOLUTION,
                SettingsDefaults.SELECT_HIGHEST_RESOLUTION,
            ),
            removeExifAfterCapture = commons.getBoolean(
                SettingsKeys.REMOVE_EXIF_AFTER_CAPTURE,
                SettingsDefaults.REMOVE_EXIF_AFTER_CAPTURE,
            ),
            gyroscopeSuggestions = commons.getBoolean(
                SettingsKeys.GYROSCOPE_SUGGESTIONS,
                SettingsDefaults.GYROSCOPE_SUGGESTIONS,
            ),
            saveImageAsPreviewed = commons.getBoolean(
                SettingsKeys.SAVE_IMAGE_AS_PREVIEW,
                SettingsDefaults.SAVE_IMAGE_AS_PREVIEW,
            ),
            saveVideoAsPreviewed = commons.getBoolean(
                SettingsKeys.SAVE_VIDEO_AS_PREVIEW,
                SettingsDefaults.SAVE_VIDEO_AS_PREVIEW,
            ),
            scanAllCodes = commons.getBoolean(
                SettingsKeys.SCAN_ALL_CODES,
                SettingsDefaults.SCAN_ALL_CODES,
            ),
        )
    }

    private fun readModeSettings(mode: CameraMode, isFrontFacing: Boolean): ModeSettings {
        val modePrefs = preferencesFor(mode)
        val qualityKey = videoQualityKey(isFrontFacing)

        return ModeSettings(
            flashMode = modePrefs.getInt(
                SettingsKeys.FLASH_MODE,
                SettingsDefaults.FLASH_MODE,
            ),
            geoTagging = modePrefs.getBoolean(
                SettingsKeys.GEO_TAGGING,
                SettingsDefaults.GEO_TAGGING,
            ),
            selfIllumination = modePrefs.getBoolean(
                SettingsKeys.SELF_ILLUMINATION,
                SettingsDefaults.SELF_ILLUMINATION,
            ),
            videoQuality = when {
                modePrefs.contains(qualityKey) -> {
                    videoQualityFromTitle(modePrefs.getString(qualityKey, "").orEmpty())
                }
                else -> SettingsDefaults.VIDEO_QUALITY
            },
        )
    }

    private fun preferencesFor(mode: CameraMode): SharedPreferences {
        return modePreferences.getValue(mode).value
    }

    private fun videoQualityKey(isFrontFacing: Boolean): String {
        return when {
            isFrontFacing -> "${SettingsKeys.VIDEO_QUALITY}_FRONT"
            else -> "${SettingsKeys.VIDEO_QUALITY}_BACK"
        }
    }

    private fun storePhotoQuality(value: Int) {
        commons.edit {
            putInt(SettingsKeys.PHOTO_QUALITY, value)
        }
    }

    private fun seedCommonDefaults() {
        commons.edit {
            seedSaveAsPreviewed(this)
            seedBarcodeFormats(this)

            if (!commons.contains(SettingsKeys.CAMERA_SOUNDS)) {
                putBoolean(
                    SettingsKeys.CAMERA_SOUNDS,
                    SettingsDefaults.CAMERA_SOUNDS,
                )
            }

            if (!commons.contains(SettingsKeys.GRID)) {
                putInt(SettingsKeys.GRID, SettingsDefaults.GRID_TYPE.ordinal)
            }

            if (!commons.contains(SettingsKeys.FOCUS_TIMEOUT)) {
                putString(
                    SettingsKeys.FOCUS_TIMEOUT,
                    focusTimeoutLabel(SettingsDefaults.FOCUS_TIMEOUT_SECONDS),
                )
            }

            if (!commons.contains(SettingsKeys.INCLUDE_AUDIO)) {
                putBoolean(
                    SettingsKeys.INCLUDE_AUDIO,
                    SettingsDefaults.INCLUDE_AUDIO,
                )
            }

            if (!commons.contains(SettingsKeys.ENABLE_EIS)) {
                putBoolean(SettingsKeys.ENABLE_EIS, SettingsDefaults.ENABLE_EIS)
            }

            if (!commons.contains(SettingsKeys.ASPECT_RATIO)) {
                putInt(SettingsKeys.ASPECT_RATIO, SettingsDefaults.ASPECT_RATIO)
            }

            if (!commons.contains(SettingsKeys.SCAN_ALL_CODES)) {
                putBoolean(
                    SettingsKeys.SCAN_ALL_CODES,
                    SettingsDefaults.SCAN_ALL_CODES,
                )
            }
        }

        migrateFromLegacyPhotoQuality()
    }

    private fun seedSaveAsPreviewed(editor: SharedPreferences.Editor) {
        if (commons.contains(SettingsKeys.SAVE_IMAGE_AS_PREVIEW)) {
            if (!commons.contains(SettingsKeys.SAVE_VIDEO_AS_PREVIEW)) {
                editor.putBoolean(SettingsKeys.SAVE_VIDEO_AS_PREVIEW, false)
            }
            return
        }

        editor.putBoolean(
            SettingsKeys.SAVE_IMAGE_AS_PREVIEW,
            SettingsDefaults.SAVE_IMAGE_AS_PREVIEW,
        )
        editor.putBoolean(
            SettingsKeys.SAVE_VIDEO_AS_PREVIEW,
            SettingsDefaults.SAVE_VIDEO_AS_PREVIEW,
        )
    }

    private fun seedBarcodeFormats(editor: SharedPreferences.Editor) {
        val qrKey = SettingsKeys.scanKey(BarcodeFormat.QR_CODE.name)

        if (commons.contains(qrKey)) {
            return
        }

        BarcodeFormat.entries.forEach { format ->
            editor.putBoolean(SettingsKeys.scanKey(format.name), false)
        }
        editor.putBoolean(qrKey, true)
    }

    private fun migrateFromLegacyPhotoQuality() {
        if (commons.contains(SettingsKeys.EMPHASIS_ON_QUALITY)) {
            if (!commons.contains(SettingsKeys.PHOTO_QUALITY)) {
                val optimizeForQuality = commons.getBoolean(
                    SettingsKeys.EMPHASIS_ON_QUALITY,
                    false,
                )
                storePhotoQuality(
                    if (optimizeForQuality) MAX_PHOTO_QUALITY else SettingsDefaults.PHOTO_QUALITY,
                )
            }

            commons.edit {
                remove(SettingsKeys.EMPHASIS_ON_QUALITY)
            }
        }

        val stored = commons.getInt(
            SettingsKeys.PHOTO_QUALITY,
            SettingsDefaults.PHOTO_QUALITY,
        )

        if (stored == 0) {
            storePhotoQuality(SettingsDefaults.PHOTO_QUALITY)
        }
    }

    private fun seedModeDefaults(mode: CameraMode, isFrontFacing: Boolean) {
        val modePrefs = preferencesFor(mode)

        modePrefs.edit {
            if (!modePrefs.contains(SettingsKeys.FLASH_MODE)) {
                putInt(SettingsKeys.FLASH_MODE, SettingsDefaults.FLASH_MODE)
            }

            if (!modePrefs.contains(SettingsKeys.GEO_TAGGING)) {
                putBoolean(SettingsKeys.GEO_TAGGING, SettingsDefaults.GEO_TAGGING)
            }

            val needsSelfIllumination = isFrontFacing &&
                !modePrefs.contains(SettingsKeys.SELF_ILLUMINATION)

            if (needsSelfIllumination) {
                putBoolean(
                    SettingsKeys.SELF_ILLUMINATION,
                    SettingsDefaults.SELF_ILLUMINATION,
                )
            }
        }
    }

    private data class SlottedMode(
        val mode: CameraMode,
        val isFrontFacing: Boolean,
    )

    private companion object {
        private const val MAX_PHOTO_QUALITY = 100
    }
}
