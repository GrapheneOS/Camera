package app.grapheneos.camera.data.settings.mapper

import androidx.camera.video.Quality
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.SettingsDefaults
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
            flashMode = SOME_FLASH_MODE,
            geoTagging = true,
            selfIllumination = true,
            videoQualityBack = StoredVideoQuality.FHD,
        )

        assertEquals(
            ModeSettings(
                flashMode = SOME_FLASH_MODE,
                geoTagging = true,
                selfIllumination = true,
                videoQuality = Quality.FHD,
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

        assertEquals(Quality.HD, mapper.map(stored = stored, isFrontFacing = true).videoQuality)
        assertEquals(Quality.UHD, mapper.map(stored = stored, isFrontFacing = false).videoQuality)
    }

    @Test
    fun map_aStoredVideoQuality_readsAsTheResolutionItNames() {
        assertEquals(Quality.UHD, mapped(StoredVideoQuality.UHD))
        assertEquals(Quality.FHD, mapped(StoredVideoQuality.FHD))
        assertEquals(Quality.HD, mapped(StoredVideoQuality.HD))
        assertEquals(Quality.SD, mapped(StoredVideoQuality.SD))
    }

    @Test
    fun map_aVideoQualityLeftToTheDevice_readsAsTheDefault() {
        assertEquals(SettingsDefaults.VIDEO_QUALITY, mapped(StoredVideoQuality.DEVICE_CHOICE))
    }

    private fun mapped(quality: StoredVideoQuality): Quality {
        val stored = StoredModeSettings(videoQualityBack = quality)

        return mapper.map(stored = stored, isFrontFacing = false).videoQuality
    }

    private companion object {
        const val SOME_FLASH_MODE = 2
    }
}
