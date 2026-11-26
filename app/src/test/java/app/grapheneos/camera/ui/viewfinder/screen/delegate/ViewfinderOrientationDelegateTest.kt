package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.orientation.repository.DeviceOrientationRepository
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.testutil.viewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ViewfinderOrientationDelegateTest {

    private val deviceOrientationRepository = mockk<DeviceOrientationRepository>()

    private val orientation = MutableSharedFlow<DeviceOrientation>()

    private val autoRotate = MutableSharedFlow<Boolean>()

    private val stateHolder = viewfinderStateHolder(
        mode = CameraMode.CAMERA,
        settings = CameraSettings(gyroscopeSuggestions = true),
    )

    @Test
    fun orientation_isPublishedToTheState() {
        runTest {
            startTracking()

            orientation.emit(DeviceOrientation.DEGREES_270)

            assertEquals(DeviceOrientation.DEGREES_270, state().deviceOrientation)
        }
    }

    @Test
    fun autoRotateAndDisplayRotation_arePublishedToTheState() {
        runTest {
            val delegate = startTracking()

            autoRotate.emit(true)
            delegate.setDisplayRotation(DeviceOrientation.DEGREES_270)

            assertTrue(state().autoRotateEnabled)
            assertEquals(DeviceOrientation.DEGREES_270, state().displayRotation)
        }
    }

    private fun state(): ViewfinderState {
        return stateHolder.state.value
    }

    private fun TestScope.startTracking(): ViewfinderOrientationDelegate {
        every { deviceOrientationRepository.orientation } returns orientation
        every { deviceOrientationRepository.autoRotateEnabled } returns autoRotate

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val delegate = ViewfinderOrientationDelegateImpl(
            deviceOrientationRepository = deviceOrientationRepository,
        )
        delegate.bind(stateHolder)
        backgroundScope.launch(dispatcher) { delegate.trackOrientation() }

        return delegate
    }
}
