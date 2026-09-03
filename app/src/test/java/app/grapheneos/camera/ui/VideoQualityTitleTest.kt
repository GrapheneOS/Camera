package app.grapheneos.camera.ui

import android.content.Context
import androidx.camera.video.Quality
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoQualityTitleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun videoQualityTitle_offeredQualities_keepTheirWording() {
        assertEquals("2160p (UHD)", videoQualityTitle(context, Quality.UHD))
        assertEquals("1080p (FHD)", videoQualityTitle(context, Quality.FHD))
        assertEquals("720p (HD)", videoQualityTitle(context, Quality.HD))
        assertEquals("480p (SD)", videoQualityTitle(context, Quality.SD))
    }

    @Test
    fun videoQualityTitle_aQualityWithNoName_fallsBackRatherThanThrowing() {
        assertEquals("Unknown", videoQualityTitle(context, Quality.HIGHEST))
    }
}
