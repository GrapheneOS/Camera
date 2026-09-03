package app.grapheneos.camera.data.settings

import androidx.camera.video.Quality
import app.grapheneos.camera.data.settings.model.videoQualityFromTitle
import app.grapheneos.camera.data.settings.model.videoQualityTitle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoQualityTitleTest {

    @Test
    fun videoQualityFromTitle_everyOfferedQuality_roundTripsThroughItsTitle() {
        val qualities = listOf(
            Quality.UHD,
            Quality.FHD,
            Quality.HD,
            Quality.SD,
        )

        qualities.forEach { quality ->
            assertEquals(quality, videoQualityFromTitle(videoQualityTitle(quality)))
        }
    }

    @Test
    fun videoQualityTitle_offeredQualities_keepTheirWording() {
        assertEquals("2160p (UHD)", videoQualityTitle(Quality.UHD))
        assertEquals("1080p (FHD)", videoQualityTitle(Quality.FHD))
        assertEquals("720p (HD)", videoQualityTitle(Quality.HD))
        assertEquals("480p (SD)", videoQualityTitle(Quality.SD))
    }

    @Test
    fun videoQualityFromTitle_unrecognizedTitle_fallsBackRatherThanThrowing() {
        assertEquals(Quality.SD, videoQualityFromTitle("4320p (8K)"))
        assertEquals(Quality.SD, videoQualityFromTitle(""))
    }
}
