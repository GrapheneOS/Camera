package app.grapheneos.camera.data.camera.session

import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.ExposureState
import app.grapheneos.camera.data.camera.mapper.CameraXStateMapper
import app.grapheneos.camera.data.camera.model.CameraExposure
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExposureControlTest {

    private val exposureState = mockk<ExposureState>()
    private val cameraControl = mockk<CameraControl>(relaxed = true)
    private val camera = mockk<Camera> {
        every { cameraControl } returns this@ExposureControlTest.cameraControl
        every { cameraInfo.exposureState } returns exposureState
    }
    private val session = mockk<CameraSession>()
    private val cameraXStateMapper = mockk<CameraXStateMapper> {
        every { map(exposureState) } returns EXPOSURE
    }
    private val exposureControl = ExposureControlImpl(
        session = session,
        cameraXStateMapper = cameraXStateMapper,
    )

    @Test
    fun boundCamera_reportsItsExposureAndTakesTheCompensation() {
        every { session.camera } returns camera

        exposureControl.setCompensationIndex(3)

        assertEquals(EXPOSURE, exposureControl.exposure)
        verify(exactly = 1) { cameraControl.setExposureCompensationIndex(3) }
    }

    @Test
    fun withoutACamera_reportsNoExposureAndIgnoresTheCompensation() {
        every { session.camera } returns null

        exposureControl.setCompensationIndex(3)

        assertNull(exposureControl.exposure)
        verify(exactly = 0) { cameraControl.setExposureCompensationIndex(any()) }
    }

    private companion object {
        val EXPOSURE = CameraExposure(
            compensationIndex = 1,
            compensationRange = -12..12,
        )
    }
}
