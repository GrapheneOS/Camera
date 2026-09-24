package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.view.Surface
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.orientation.model.DeviceMotion
import app.grapheneos.camera.data.orientation.repository.DeviceOrientationRepository
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.mapper.LevelUiStateMapperImpl
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
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

    private val stateHolder = ViewfinderStateHolder(
        initial = ViewfinderState(
            mode = CameraMode.CAMERA,
            requiresVideoModeOnly = false,
            settings = CameraSettings(gyroscopeSuggestions = true),
        ),
        render = { ViewfinderUiState() },
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
            val delegate = startTracking()
            val announcements = collectAnnouncements(delegate)
            motion.emit(motionOf(tilt = 2))
            motion.emit(motionOf(tilt = 0))

            advanceTimeBy(LEVEL_HOLD - 1.milliseconds)
            assertEquals(0, announcements.size)

            advanceTimeBy(2.milliseconds)
            assertEquals(1, announcements.size)
        }
    }

    @Test
    fun levelReached_isNotAnnouncedAgainUntilTheDeviceTiltsAway() {
        runTest {
            val delegate = startTracking()
            val announcements = collectAnnouncements(delegate)
            motion.emit(motionOf(tilt = 2))
            motion.emit(motionOf(tilt = 0))
            advanceTimeBy(LEVEL_HOLD + 1.milliseconds)

            motion.emit(motionOf(tilt = 2))
            motion.emit(motionOf(tilt = 0))
            advanceTimeBy(LEVEL_HOLD + 1.milliseconds)
            assertEquals(1, announcements.size)

            motion.emit(motionOf(tilt = 6))
            motion.emit(motionOf(tilt = 0))
            advanceTimeBy(LEVEL_HOLD + 1.milliseconds)
            assertEquals(2, announcements.size)
        }
    }

    private fun state(): ViewfinderState {
        return stateHolder.state.value
    }

    private fun TestScope.collectAnnouncements(
        delegate: ViewfinderOrientationDelegate,
    ): List<Unit> {
        val announcements = mutableListOf<Unit>()
        backgroundScope.launch { delegate.levelReachedEvents.collect { announcements += it } }

        return announcements
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
