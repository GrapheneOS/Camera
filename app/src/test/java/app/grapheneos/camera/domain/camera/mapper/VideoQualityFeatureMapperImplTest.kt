package app.grapheneos.camera.domain.camera.mapper

import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.video.GroupableFeatures
import androidx.camera.video.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoQualityFeatureMapperImplTest {

    private val mapper = VideoQualityFeatureMapperImpl()

    @Test
    fun map_aQualityNamingAResolution_hasAGroupableEquivalent() {
        assertEquals(GroupableFeatures.UHD_RECORDING, mapper.map(Quality.UHD))
        assertEquals(GroupableFeatures.FHD_RECORDING, mapper.map(Quality.FHD))
        assertEquals(GroupableFeatures.HD_RECORDING, mapper.map(Quality.HD))
        assertEquals(GroupableFeatures.SD_RECORDING, mapper.map(Quality.SD))
    }

    @Test
    fun map_aQualityNamingNoResolution_hasNoGroupableEquivalent() {
        assertNull(mapper.map(Quality.HIGHEST))
        assertNull(mapper.map(Quality.LOWEST))
    }

    @Test
    fun map_aFeatureNamingNoQuality_namesNoQuality() {
        assertNull(mapper.map(GroupableFeature.PREVIEW_STABILIZATION))
        assertNull(mapper.map(GroupableFeatures.VIDEO_STABILIZATION))
    }

    @Test
    fun map_everyGroupableQuality_readsBackAsItself() {
        GROUPABLE_QUALITIES.forEach { quality ->
            val feature = mapper.map(quality)

            assertEquals(quality, feature?.let { mapper.map(it) })
        }
    }

    private companion object {
        val GROUPABLE_QUALITIES = listOf(
            Quality.UHD,
            Quality.FHD,
            Quality.HD,
            Quality.SD,
        )
    }
}
