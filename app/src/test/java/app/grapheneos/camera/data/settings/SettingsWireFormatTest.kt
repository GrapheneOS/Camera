package app.grapheneos.camera.data.settings

import androidx.camera.core.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapper
import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapperImpl
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import app.grapheneos.camera.data.settings.store.StoredModeSettings
import app.grapheneos.camera.data.settings.store.StoredVideoQuality
import app.grapheneos.camera.data.settings.store.settingsPrefsSerializer
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsWireFormatTest {

    private val mapper: CameraSettingsMapper = CameraSettingsMapperImpl()

    private val everySettingChosen = CameraSettings(
        aspectRatio = AspectRatio.RATIO_16_9,
        gridType = GridType.GOLDEN_RATIO,
        focusTimeoutSeconds = SOME_FOCUS_TIMEOUT_SECONDS,
        selfTimerDurationSeconds = SOME_SELF_TIMER_DURATION,
        enableCameraSounds = false,
        includeAudio = false,
        enableEis = false,
        enableZsl = true,
        waitForFocusLock = true,
        selectHighestResolution = true,
        photoQuality = SOME_PHOTO_QUALITY,
        removeExifAfterCapture = false,
        gyroscopeSuggestions = true,
        saveImageAsPreviewed = false,
        saveVideoAsPreviewed = false,
        scanAllCodes = true,
        enabledBarcodeFormats = setOf(SOME_BARCODE_FORMAT),
    )

    private fun encode(settings: CameraSettings): String {
        val output = ByteArrayOutputStream()

        runBlocking {
            settingsPrefsSerializer.writeTo(
                SettingsPrefs(common = mapper.map(settings)),
                output,
            )
        }

        return output.toByteArray().decodeToString()
    }

    private fun decode(stored: String): CameraSettings {
        return runBlocking {
            settingsPrefsSerializer.readFrom(stored.encodeToByteArray().inputStream())
        }.common.let(mapper::map)
    }

    @Test
    fun everySetting_chosen_isStoredUnderTheKeyItHasAlwaysHad() {
        assertEquals(EVERY_SETTING_ON_DISK, encode(everySettingChosen))
    }

    @Test
    fun everySetting_stored_readsBackAsWhatWasChosen() {
        assertEquals(everySettingChosen, decode(EVERY_SETTING_ON_DISK))
    }

    @Test
    fun nothingChosen_isNotWrittenAtAll() {
        assertEquals("{}", encode(CameraSettings()))
    }

    @Test
    fun decode_aKeyFromALaterVersion_isSkipped() {
        val stored = """{"common":{"photo_quality":71,"a_setting_from_the_future":true}}"""

        assertEquals(SOME_PHOTO_QUALITY, decode(stored).photoQuality)
    }

    @Test
    fun gridType_everyConstant_roundTripsThroughItsStoredName() {
        GridType.entries.forEach { grid ->
            assertEquals(grid, decode(encode(CameraSettings(gridType = grid))).gridType)
        }
    }

    @Test
    fun gridType_aNameThisVersionNoLongerHas_readsAsTheDefault() {
        val stored = """{"common":{"grid_type":"SPIRAL_OF_THEODORUS","photo_quality":71}}"""

        val settings = decode(stored)

        assertEquals(SettingsDefaults.GRID_TYPE, settings.gridType)
        assertEquals(SOME_PHOTO_QUALITY, settings.photoQuality)
    }

    private fun encodeMode(mode: StoredModeSettings): String {
        val output = ByteArrayOutputStream()

        runBlocking {
            settingsPrefsSerializer.writeTo(SettingsPrefs(modes = mapOf(MODE to mode)), output)
        }

        return output.toByteArray().decodeToString()
    }

    private fun decodeMode(stored: String): StoredModeSettings {
        return runBlocking {
            settingsPrefsSerializer.readFrom(stored.encodeToByteArray().inputStream())
        }.modes.getValue(MODE)
    }

    @Test
    fun everyModeSetting_chosen_isStoredUnderTheKeyItHasAlwaysHad() {
        val mode = StoredModeSettings(
            flashMode = SOME_FLASH_MODE,
            geoTagging = true,
            selfIllumination = true,
            videoQualityFront = StoredVideoQuality.HD,
            videoQualityBack = StoredVideoQuality.UHD,
        )

        assertEquals(EVERY_MODE_SETTING_ON_DISK, encodeMode(mode))
    }

    @Test
    fun videoQuality_everyConstant_roundTripsThroughItsStoredName() {
        StoredVideoQuality.entries.forEach { quality ->
            val stored = encodeMode(StoredModeSettings(videoQualityBack = quality))

            assertEquals(quality, decodeMode(stored).videoQualityBack)
        }
    }

    @Test
    fun videoQuality_leftToTheDevice_isNotWrittenAtAll() {
        assertEquals("""{"modes":{"VIDEO":{}}}""", encodeMode(StoredModeSettings()))
    }

    @Test
    fun videoQuality_aNameThisVersionNoLongerHas_readsAsLeftToTheDevice() {
        val stored = """{"modes":{"VIDEO":{"flash_mode":2,"video_quality_back":"EIGHT_K"}}}"""

        val mode = decodeMode(stored)

        assertEquals(StoredVideoQuality.DEVICE_CHOICE, mode.videoQualityBack)
        assertEquals(SOME_FLASH_MODE, mode.flashMode)
    }

    @Test
    fun modeNames_areStableWireKeys() {
        assertEquals(
            mapOf(
                CameraMode.QR_SCAN to "QR_SCAN",
                CameraMode.AUTO to "AUTO",
                CameraMode.FACE_RETOUCH to "FACE_RETOUCH",
                CameraMode.PORTRAIT to "PORTRAIT",
                CameraMode.NIGHT to "NIGHT",
                CameraMode.HDR to "HDR",
                CameraMode.CAMERA to "CAMERA",
                CameraMode.VIDEO to "VIDEO",
            ),
            CameraMode.entries.associateWith(SettingsPrefs::storedModeName),
        )
    }

    private companion object {
        const val MODE = "VIDEO"
        const val SOME_FLASH_MODE = 2
        const val SOME_FOCUS_TIMEOUT_SECONDS = 10L
        const val SOME_SELF_TIMER_DURATION = 3
        const val SOME_PHOTO_QUALITY = 71
        const val SOME_BARCODE_FORMAT = "AZTEC"

        const val EVERY_SETTING_ON_DISK = """{"common":{"aspect_ratio":1,""" +
            """"grid_type":"GOLDEN_RATIO","focus_timeout_seconds":10,""" +
            """"self_timer_duration_seconds":3,"enable_camera_sounds":false,""" +
            """"include_audio":false,"enable_eis":false,"enable_zsl":true,""" +
            """"wait_for_focus_lock":true,"select_highest_resolution":true,""" +
            """"photo_quality":71,"remove_exif_after_capture":false,""" +
            """"gyroscope_suggestions":true,"save_image_as_previewed":false,""" +
            """"save_video_as_previewed":false,"scan_all_codes":true,""" +
            """"enabled_barcode_formats":["AZTEC"]}}"""

        const val EVERY_MODE_SETTING_ON_DISK = """{"modes":{"VIDEO":{"flash_mode":2,""" +
            """"geo_tagging":true,"self_illumination":true,""" +
            """"video_quality_front":"HD","video_quality_back":"UHD"}}}"""
    }
}
