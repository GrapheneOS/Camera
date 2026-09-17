package app.grapheneos.camera.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.data.core.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoQualityTitleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun videoQualityTitle_offeredQualities_keepTheirWording() {
        assertEquals("2160p (UHD)", videoQualityTitle(context, VideoQuality.UHD))
        assertEquals("1080p (FHD)", videoQualityTitle(context, VideoQuality.FHD))
        assertEquals("720p (HD)", videoQualityTitle(context, VideoQuality.HD))
        assertEquals("480p (SD)", videoQualityTitle(context, VideoQuality.SD))
    }

    @Test
    fun videoQualityTitle_aQualityWithNoName_fallsBackRatherThanThrowing() {
        assertEquals("Unknown", videoQualityTitle(context, VideoQuality.HIGHEST))
    }
}
