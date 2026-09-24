package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.orientation.model.DeviceMotion
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.ui.viewfinder.screen.model.LevelUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LevelUiStateMapperTest {

    private val mapper = LevelUiStateMapperImpl()

    private val photoState = ViewfinderState(
        mode = CameraMode.CAMERA,
        requiresVideoModeOnly = false,
        settings = CameraSettings(gyroscopeSuggestions = true),
    )

    @Test
    fun level_appearsOnlyOnceTheDeviceHoldsSteadyNearLevel() {
        val farOff = map(previous = null, tilt = 30)
        assertFalse(farOff.visible)

        val jumpedClose = map(current = farOff, previous = motionOf(tilt = 30), tilt = 2)
        assertFalse(jumpedClose.visible)

        assertEquals(
            LevelUiState(visible = true, tiltDegrees = 1, horizonDegrees = 0),
            map(current = jumpedClose, previous = motionOf(tilt = 2), tilt = 1),
        )
    }

    @Test
    fun level_disappearsBeyondItsRange() {
        val shown = LevelUiState(visible = true, tiltDegrees = 1)

        assertFalse(map(current = shown, previous = motionOf(tilt = 1), tilt = 50).visible)
    }

    @Test
    fun level_staysHiddenWithTheSuggestionsTurnedOff() {
        val state = photoState.copy(settings = CameraSettings(gyroscopeSuggestions = false))

        assertFalse(map(previous = motionOf(tilt = 1), tilt = 1, state = state).visible)
    }

    @Test
    fun level_holdsItsAnglesWhileTheSelfTimerCountsDown() {
        val shown = LevelUiState(visible = true, tiltDegrees = 1)
        val state = photoState.copy(capture = ViewfinderCaptureState(isSelfTimerRunning = true))

        assertEquals(
            shown,
            map(
                current = shown,
                previous = motionOf(tilt = 1),
                tilt = 3,
                state = state,
            ),
        )
    }

    private fun map(
        current: LevelUiState = LevelUiState(),
        previous: DeviceMotion?,
        tilt: Int,
        state: ViewfinderState = photoState,
    ): LevelUiState {
        return mapper.map(
            current = current,
            previousMotion = previous,
            motion = motionOf(tilt = tilt),
            state = state,
        )
    }

    private fun motionOf(tilt: Int): DeviceMotion {
        return DeviceMotion(
            orientation = DeviceOrientation.DEGREES_0,
            tiltDegrees = tilt,
            horizonDegrees = 0,
        )
    }
}
