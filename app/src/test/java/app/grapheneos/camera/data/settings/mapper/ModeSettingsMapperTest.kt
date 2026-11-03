package app.grapheneos.camera.data.settings.mapper

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.core.model.ModeSlot
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.data.settings.store.StoredFlashMode
import app.grapheneos.camera.data.settings.store.StoredModeSettings
import app.grapheneos.camera.data.settings.store.StoredVideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModeSettingsMapperTest {

    private val mapper = ModeSettingsMapperImpl()

    @Test
    fun map_settingsNeverConfigured_readAsTheDeclaredDefaults() {
        val mapped = mapper.map(stored = StoredModeSettings(), slot = BACK_PHOTO)

        assertEquals(SettingsDefaults.FLASH_MODE, mapped.flashMode)
        assertEquals(SettingsDefaults.VIDEO_QUALITY, mapped.videoQuality)
    }

    @Test
    fun map_everySettingConfigured_isCarriedOver() {
        val stored = StoredModeSettings(
            flashModeBack = StoredFlashMode.AUTO,
            videoQualityBack = StoredVideoQuality.FHD,
        )

        assertEquals(
            ModeSettings(
                flashMode = FlashMode.AUTO,
                videoQuality = VideoQuality.FHD,
            ),
            mapper.map(stored = stored, slot = BACK_PHOTO),
        )
    }

    @Test
    fun map_theSlottedFacing_isTheOnlyFlashModeExposed() {
        val stored = StoredModeSettings(
            flashModeFront = StoredFlashMode.LOW_LIGHT_BOOST,
            flashModeBack = StoredFlashMode.AUTO,
        )

        val front = mapper.map(stored = stored, slot = FRONT_PHOTO)
        val back = mapper.map(stored = stored, slot = BACK_PHOTO)

        assertEquals(FlashMode.LOW_LIGHT_BOOST, front.flashMode)
        assertEquals(FlashMode.AUTO, back.flashMode)
    }

    @Test
    fun map_theSlottedFacing_isTheOnlyVideoQualityExposed() {
        val stored = StoredModeSettings(
            videoQualityFront = StoredVideoQuality.HD,
            videoQualityBack = StoredVideoQuality.UHD,
        )

        val front = mapper.map(stored = stored, slot = FRONT_PHOTO)
        val back = mapper.map(stored = stored, slot = BACK_PHOTO)

        assertEquals(VideoQuality.HD, front.videoQuality)
        assertEquals(VideoQuality.UHD, back.videoQuality)
    }

    @Test
    fun map_theSlottedFacing_isTheOnlyFrameRateExposed() {
        val stored = StoredModeSettings(
            frameRateFront = 30,
            frameRateBack = 60,
        )

        val front = mapper.map(stored = stored, slot = FRONT_PHOTO)
        val back = mapper.map(stored = stored, slot = BACK_PHOTO)

        assertEquals(VideoFrameRate.FPS_30, front.videoFrameRate)
        assertEquals(VideoFrameRate.FPS_60, back.videoFrameRate)
    }

    @Test
    fun map_anUnknownFrameRate_readsAsTheDefault() {
        val stored = StoredModeSettings(frameRateBack = 24)
        val mapped = mapper.map(stored = stored, slot = BACK_PHOTO)

        assertEquals(SettingsDefaults.VIDEO_FRAME_RATE, mapped.videoFrameRate)
    }

    @Test
    fun map_aStoredVideoQuality_readsAsTheResolutionItNames() {
        assertEquals(VideoQuality.UHD, mapped(StoredVideoQuality.UHD))
        assertEquals(VideoQuality.FHD, mapped(StoredVideoQuality.FHD))
        assertEquals(VideoQuality.HD, mapped(StoredVideoQuality.HD))
        assertEquals(VideoQuality.SD, mapped(StoredVideoQuality.SD))
    }

    @Test
    fun map_aVideoQualityLeftToTheDevice_readsAsTheDefault() {
        assertEquals(SettingsDefaults.VIDEO_QUALITY, mapped(StoredVideoQuality.DEVICE_CHOICE))
    }

    @Test
    fun map_aStoredFlashMode_readsAsTheModeItNames() {
        assertEquals(FlashMode.AUTO, mappedFlashMode(StoredFlashMode.AUTO))
        assertEquals(FlashMode.ON, mappedFlashMode(StoredFlashMode.ON))
        assertEquals(FlashMode.OFF, mappedFlashMode(StoredFlashMode.OFF))
        assertEquals(
            FlashMode.LOW_LIGHT_BOOST,
            mappedFlashMode(StoredFlashMode.LOW_LIGHT_BOOST),
        )
    }

    @Test
    fun map_aFlashModeThisVersionDoesNotKnow_readsAsTheDefault() {
        assertEquals(SettingsDefaults.FLASH_MODE, mappedFlashMode(StoredFlashMode.UNKNOWN))
    }

    @Test
    fun map_aVideoLightSavedBefore_readsAsOff() {
        val settings = StoredModeSettings(flashModeBack = StoredFlashMode.ON)

        assertEquals(FlashMode.OFF, mapper.map(stored = settings, slot = BACK_VIDEO).flashMode)
    }

    @Test
    fun map_temporaryLights_returnNull() {
        assertNull(mapper.map(flashMode = FlashMode.ON, mode = CameraMode.VIDEO))
        CameraMode.entries.forEach { mode ->
            assertNull(
                mapper.map(
                    flashMode = FlashMode.SELF_ILLUMINATION,
                    mode = mode,
                ),
            )
        }
    }

    @Test
    fun map_savedModes_readBackAsThemselves() {
        val saved = FlashMode.entries - FlashMode.SELF_ILLUMINATION

        saved.forEach { flashMode ->
            val stored = requireNotNull(
                mapper.map(
                    flashMode = flashMode,
                    mode = CameraMode.CAMERA,
                ),
            )

            assertEquals(flashMode, mappedFlashMode(stored))
        }
    }

    private fun mapped(quality: StoredVideoQuality): VideoQuality {
        val stored = StoredModeSettings(videoQualityBack = quality)

        return mapper.map(stored = stored, slot = BACK_PHOTO).videoQuality
    }

    private fun mappedFlashMode(stored: StoredFlashMode): FlashMode {
        val settings = StoredModeSettings(flashModeBack = stored)

        return mapper.map(stored = settings, slot = BACK_PHOTO).flashMode
    }

    private companion object {
        val BACK_PHOTO = ModeSlot(mode = CameraMode.CAMERA, lensFacing = LensFacing.BACK)
        val FRONT_PHOTO = ModeSlot(mode = CameraMode.CAMERA, lensFacing = LensFacing.FRONT)
        val BACK_VIDEO = ModeSlot(mode = CameraMode.VIDEO, lensFacing = LensFacing.BACK)
    }
}
