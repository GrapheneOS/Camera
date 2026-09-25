package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.view.Surface
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.orientation.model.DeviceMotion
import app.grapheneos.camera.data.orientation.repository.DeviceOrientationRepository
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.testutil.collectEffects
import app.grapheneos.camera.testutil.viewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.mapper.LevelUiStateMapperImpl
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun autoRotateAndDisplayRotation_arePublishedToTheState() {
        runTest {
            val delegate = startTracking()

            autoRotate.emit(true)
            delegate.setDisplayRotation(Surface.ROTATION_90)

            assertTrue(state().autoRotateEnabled)
            assertEquals(Surface.ROTATION_90, state().displayRotation)
        }
    }

    @Test
    fun level_disappearsOutsidePhotoMode() {
        runTest {
            val delegate = startTracking()
            motion.emit(motionOf(tilt = 1))

            stateHolder.update { it.copy(mode = CameraMode.VIDEO) }

            assertFalse(delegate.levelUiState.value.visible)
        }
    }

    @Test
    fun levelReached_isAnnouncedOnceTheDeviceStaysLevel() {
        runTest {
            startTracking()
            val effects = collectEffects(stateHolder)
            motion.emit(motionOf(tilt = 2))
            motion.emit(motionOf(tilt = 0))

            advanceTimeBy(LEVEL_HOLD - 1.milliseconds)
            assertEquals(0, hapticCount(effects))

            advanceTimeBy(2.milliseconds)
            assertEquals(1, hapticCount(effects))
        }
    }

    @Test
    fun levelReached_isNotAnnouncedAgainUntilTheDeviceTiltsAway() {
        runTest {
            startTracking()
            val effects = collectEffects(stateHolder)
            motion.emit(motionOf(tilt = 2))
            motion.emit(motionOf(tilt = 0))
            advanceTimeBy(LEVEL_HOLD + 1.milliseconds)

            motion.emit(motionOf(tilt = 2))
            motion.emit(motionOf(tilt = 0))
            advanceTimeBy(LEVEL_HOLD + 1.milliseconds)
            assertEquals(1, hapticCount(effects))

            motion.emit(motionOf(tilt = 6))
            motion.emit(motionOf(tilt = 0))
            advanceTimeBy(LEVEL_HOLD + 1.milliseconds)
            assertEquals(2, hapticCount(effects))
        }
    }

    private fun hapticCount(effects: List<Effect>): Int {
        return effects.count { effect -> effect == Effect.PlayLevelHaptic }
    }

    private fun state(): ViewfinderState {
        return stateHolder.state.value
    }

    private fun TestScope.startTracking(): ViewfinderOrientationDelegate {
        every { deviceOrientationRepository.motion() } returns motion
        every { deviceOrientationRepository.autoRotateEnabled() } returns autoRotate

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val delegate = ViewfinderOrientationDelegateImpl(
            deviceOrientationRepository = deviceOrientationRepository,
            levelUiStateMapper = LevelUiStateMapperImpl(),
            mainDispatcher = dispatcher,
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
            tiltDegrees = tilt,
            horizonDegrees = 0,
        )
    }

    private companion object {
        val LEVEL_HOLD = 250.milliseconds
    }
}
