package app.grapheneos.camera.data.camera.mapper

import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.core.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoQualityMapperTest {

    private val mapper = VideoQualityMapperImpl()
    private val camera = BoundCamera(
        mode = CameraMode.VIDEO,
        isVideoMode = true,
        lensFacing = LensFacing.BACK,
        hasFlashUnit = true,
        videoQualities = listOf(VideoQuality.UHD, VideoQuality.FHD, VideoQuality.HD),
    )

    @Test
    fun highestQuality_usesTheHighestOfferedQuality() {
        assertEquals(
            VideoQuality.UHD,
            mapper.map(
                camera = camera,
                requestedQuality = VideoQuality.HIGHEST,
            ),
        )
    }

    @Test
    fun highestQuality_withoutAnOfferedQuality_keepsTheDeviceDefault() {
        assertEquals(
            VideoQuality.HIGHEST,
            mapper.map(
                camera = camera.copy(videoQualities = emptyList()),
                requestedQuality = VideoQuality.HIGHEST,
            ),
        )
    }

    @Test
    fun explicitQuality_keepsTheRequestedQuality() {
        assertEquals(
            VideoQuality.HD,
            mapper.map(
                camera = camera,
                requestedQuality = VideoQuality.HD,
            ),
        )
    }
}
