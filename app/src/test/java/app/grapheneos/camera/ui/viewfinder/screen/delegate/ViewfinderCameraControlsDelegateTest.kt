package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.data.camera.session.ExposureControl
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.testutil.viewfinderStateHolder
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderCameraControlsDelegateTest {

    private val exposureControl = mockk<ExposureControl>(relaxed = true)

    private val stateHolder = viewfinderStateHolder(mode = CameraMode.CAMERA)

    @Test
    fun setExposureCompensation_isAppliedAndPublished() {
        stateHolder.update { it.copy(session = it.session.copy(exposure = EXPOSURE)) }

        val delegate = ViewfinderCameraControlsDelegateImpl(exposureControl = exposureControl)
        delegate.bind(stateHolder)
        delegate.setExposureCompensation(compensationIndex = 5)

        verify(exactly = 1) { exposureControl.setCompensationIndex(5) }
        assertEquals(5, stateHolder.state.value.session.exposure?.compensationIndex)
    }

    private companion object {
        val EXPOSURE = CameraExposure(
            compensationIndex = 1,
            compensationRange = -12..12,
        )
    }
}
