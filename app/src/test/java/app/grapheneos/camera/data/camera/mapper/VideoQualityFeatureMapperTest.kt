package app.grapheneos.camera.data.camera.mapper

import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.video.GroupableFeatures
import app.grapheneos.camera.data.core.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoQualityFeatureMapperTest {

    private val mapper = VideoQualityFeatureMapperImpl()

    @Test
    fun map_aQualityNamingAResolution_hasAGroupableEquivalent() {
        assertEquals(GroupableFeatures.UHD_RECORDING, mapper.map(VideoQuality.UHD))
        assertEquals(GroupableFeatures.FHD_RECORDING, mapper.map(VideoQuality.FHD))
        assertEquals(GroupableFeatures.HD_RECORDING, mapper.map(VideoQuality.HD))
        assertEquals(GroupableFeatures.SD_RECORDING, mapper.map(VideoQuality.SD))
    }

    @Test
    fun map_aQualityNamingNoResolution_hasNoGroupableEquivalent() {
        assertNull(mapper.map(VideoQuality.HIGHEST))
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
            VideoQuality.UHD,
            VideoQuality.FHD,
            VideoQuality.HD,
            VideoQuality.SD,
        )
    }
}
