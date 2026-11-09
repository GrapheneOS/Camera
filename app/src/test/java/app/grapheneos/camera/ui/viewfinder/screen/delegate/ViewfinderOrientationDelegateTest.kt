package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.orientation.model.DeviceMotion
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

    private val motion = MutableSharedFlow<DeviceMotion>()

    private val autoRotate = MutableSharedFlow<Boolean>()

    private val stateHolder = viewfinderStateHolder(
        mode = CameraMode.CAMERA,
        settings = CameraSettings(gyroscopeSuggestions = true),
    )

    @Test
    fun motion_publishesTheOrientationToTheState() {
        runTest {
            startTracking()

            motion.emit(motionOf(orientation = DeviceOrientation.DEGREES_270))

            assertEquals(DeviceOrientation.DEGREES_270, state().deviceOrientation)
        }
    }

    @Test
    fun motionWithTheSameOrientation_doesNotRecomputeViewfinderUiState() {
        runTest {
            startTracking()
            var projections = 0
            val orientation = stateHolder.derive { state ->
                projections += 1
                state.deviceOrientation
            }
            motion.emit(motionOf(orientation = DeviceOrientation.DEGREES_0))
            val before = projections

            repeat(50) { tilt ->
                motion.emit(motionOf(orientation = DeviceOrientation.DEGREES_0, tilt = tilt))
            }

            assertEquals(before, projections)
            assertEquals(DeviceOrientation.DEGREES_0, orientation.value)
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
        every { deviceOrientationRepository.motion } returns motion
        every { deviceOrientationRepository.autoRotateEnabled } returns autoRotate

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val delegate = ViewfinderOrientationDelegateImpl(
            deviceOrientationRepository = deviceOrientationRepository,
        )
        delegate.bind(stateHolder)
        backgroundScope.launch(dispatcher) { delegate.trackOrientation() }

        return delegate
    }

    private fun motionOf(
        orientation: DeviceOrientation = DeviceOrientation.DEGREES_0,
        tilt: Int = 0,
    ): DeviceMotion {
        return DeviceMotion(
            orientation = orientation,
            tiltDegrees = tilt.toFloat(),
            horizonDegrees = 0f,
        )
    }
}
