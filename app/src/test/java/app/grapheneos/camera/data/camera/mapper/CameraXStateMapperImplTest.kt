package app.grapheneos.camera.data.camera.mapper

import android.util.Range
import android.util.Rational
import androidx.camera.core.ExposureState
import androidx.camera.core.ZoomState
import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.data.camera.model.CameraZoom
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraXStateMapperImplTest {

    private val mapper = CameraXStateMapperImpl()

    @Test
    fun map_aZoomState_carriesEveryRatio() {
        val zoomState = mockk<ZoomState> {
            every { zoomRatio } returns 2f
            every { linearZoom } returns 0.5f
            every { minZoomRatio } returns 0.7f
            every { maxZoomRatio } returns 10f
        }

        assertEquals(
            CameraZoom(
                zoomRatio = 2f,
                linearZoom = 0.5f,
                minZoomRatio = 0.7f,
                maxZoomRatio = 10f,
            ),
            mapper.map(zoomState),
        )
    }

    @Test
    fun map_anExposureState_carriesTheIndexAndItsRange() {
        val exposureState = mockk<ExposureState> {
            every { exposureCompensationIndex } returns -3
            every { exposureCompensationRange } returns Range(-12, 12)
            every { exposureCompensationStep } returns Rational(1, 6)
        }

        assertEquals(
            CameraExposure(
                compensationIndex = -3,
                compensationRange = -12..12,
            ),
            mapper.map(exposureState),
        )
    }
}
