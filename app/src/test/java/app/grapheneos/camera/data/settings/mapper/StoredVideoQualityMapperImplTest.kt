package app.grapheneos.camera.data.settings.mapper

import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.store.StoredModeSettings
import app.grapheneos.camera.data.settings.store.StoredVideoQuality
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoredVideoQualityMapperImplTest {

    private val mapper = StoredVideoQualityMapperImpl()

    @Test
    fun map_aQualityNamingAResolution_isStoredUnderThatName() {
        assertEquals(StoredVideoQuality.UHD, mapper.map(VideoQuality.UHD))
        assertEquals(StoredVideoQuality.FHD, mapper.map(VideoQuality.FHD))
        assertEquals(StoredVideoQuality.HD, mapper.map(VideoQuality.HD))
        assertEquals(StoredVideoQuality.SD, mapper.map(VideoQuality.SD))
    }

    @Test
    fun map_aQualityNamingNoResolution_isLeftToTheDevice() {
        assertEquals(StoredVideoQuality.DEVICE_CHOICE, mapper.map(VideoQuality.HIGHEST))
    }

    @Test
    fun map_everyOfferedQuality_readsBackAsItself() {
        val readBack = ModeSettingsMapperImpl()

        OFFERED_QUALITIES.forEach { quality ->
            val stored = StoredModeSettings(videoQualityBack = mapper.map(quality))

            assertEquals(
                quality,
                readBack.map(stored = stored, isFrontFacing = false).videoQuality,
            )
        }
    }

    private companion object {
        val OFFERED_QUALITIES = listOf(
            VideoQuality.UHD,
            VideoQuality.FHD,
            VideoQuality.HD,
            VideoQuality.SD,
        )
    }
}
