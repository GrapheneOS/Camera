package app.grapheneos.camera.data.settings.mapper

import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.data.settings.store.StoredFlashMode
import app.grapheneos.camera.data.settings.store.StoredModeSettings
import app.grapheneos.camera.data.settings.store.StoredVideoQuality
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModeSettingsMapperImplTest {

    private val mapper = ModeSettingsMapperImpl()

    @Test
    fun map_settingsNeverConfigured_readAsTheDeclaredDefaults() {
        val mapped = mapper.map(stored = StoredModeSettings(), isFrontFacing = false)

        assertEquals(SettingsDefaults.FLASH_MODE, mapped.flashMode)
        assertEquals(SettingsDefaults.GEO_TAGGING, mapped.geoTagging)
        assertEquals(SettingsDefaults.SELF_ILLUMINATION, mapped.selfIllumination)
        assertEquals(SettingsDefaults.VIDEO_QUALITY, mapped.videoQuality)
    }

    @Test
    fun map_everySettingConfigured_isCarriedOver() {
        val stored = StoredModeSettings(
            flashMode = StoredFlashMode.AUTO,
            geoTagging = true,
            selfIllumination = true,
            videoQualityBack = StoredVideoQuality.FHD,
        )

        assertEquals(
            ModeSettings(
                flashMode = FlashMode.AUTO,
                geoTagging = true,
                selfIllumination = true,
                videoQuality = VideoQuality.FHD,
            ),
            mapper.map(stored = stored, isFrontFacing = false),
        )
    }

    @Test
    fun map_theSlottedFacing_isTheOnlyVideoQualityExposed() {
        val stored = StoredModeSettings(
            videoQualityFront = StoredVideoQuality.HD,
            videoQualityBack = StoredVideoQuality.UHD,
        )

        val front = mapper.map(stored = stored, isFrontFacing = true)
        val back = mapper.map(stored = stored, isFrontFacing = false)

        assertEquals(VideoQuality.HD, front.videoQuality)
        assertEquals(VideoQuality.UHD, back.videoQuality)
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

    private fun mapped(quality: StoredVideoQuality): VideoQuality {
        val stored = StoredModeSettings(videoQualityBack = quality)

        return mapper.map(stored = stored, isFrontFacing = false).videoQuality
    }

    @Test
    fun map_aStoredFlashMode_readsAsTheModeItNames() {
        assertEquals(FlashMode.AUTO, mappedFlashMode(StoredFlashMode.AUTO))
        assertEquals(FlashMode.ON, mappedFlashMode(StoredFlashMode.ON))
        assertEquals(FlashMode.OFF, mappedFlashMode(StoredFlashMode.OFF))
    }

    @Test
    fun map_aFlashModeThisVersionDoesNotKnow_readsAsTheDefault() {
        assertEquals(SettingsDefaults.FLASH_MODE, mappedFlashMode(StoredFlashMode.UNKNOWN))
    }

    @Test
    fun map_everyFlashMode_readsBackAsItself() {
        FlashMode.entries.forEach { flashMode ->
            assertEquals(flashMode, mappedFlashMode(mapper.map(flashMode)))
        }
    }

    private fun mappedFlashMode(stored: StoredFlashMode): FlashMode {
        val settings = StoredModeSettings(flashMode = stored)

        return mapper.map(stored = settings, isFrontFacing = false).flashMode
    }
}
