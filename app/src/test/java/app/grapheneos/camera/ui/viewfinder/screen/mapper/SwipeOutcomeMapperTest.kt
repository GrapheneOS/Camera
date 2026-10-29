package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.SwipeDirection
import app.grapheneos.camera.ui.viewfinder.screen.model.SwipeOutcome
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderRecordingState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SwipeOutcomeMapperTest {

    private val mapper = SwipeOutcomeMapperImpl()

    private val photoState = ViewfinderState(
        mode = CameraMode.CAMERA,
        requiresVideoModeOnly = false,
        showsCameraModeTabs = true,
    )

    @Test
    fun sidewaysSwipes_moveAcrossTheModeTabs() {
        assertEquals(
            SwipeOutcome.SelectAdjacentMode(offset = 1),
            mapper.map(direction = SwipeDirection.LEFT, state = photoState),
        )
        assertEquals(
            SwipeOutcome.SelectAdjacentMode(offset = -1),
            mapper.map(direction = SwipeDirection.RIGHT, state = photoState),
        )
    }

    @Test
    fun swipeUp_opensTheSettings() {
        assertEquals(
            SwipeOutcome.OpenSettingsPanel,
            mapper.map(direction = SwipeDirection.UP, state = photoState),
        )
    }

    @Test
    fun swipeDown_closesOpenSettingsAndOtherwiseDoesNothing() {
        assertNull(mapper.map(direction = SwipeDirection.DOWN, state = photoState))
        assertEquals(
            SwipeOutcome.CloseSettingsPanel,
            mapper.map(
                direction = SwipeDirection.DOWN,
                state = photoState.copy(isSettingsPanelOpen = true),
            ),
        )
    }

    @Test
    fun swipes_whileTheSettingsAreOpen_neitherReopenThemNorChangeTheMode() {
        val open = photoState.copy(isSettingsPanelOpen = true)

        assertNull(mapper.map(direction = SwipeDirection.UP, state = open))
        assertNull(mapper.map(direction = SwipeDirection.LEFT, state = open))
    }

    @Test
    fun swipeUp_inQrMode_offersTheFormatsUnlessAllAreScanned() {
        val qrState = photoState.copy(mode = CameraMode.QR_SCAN)
        val scanningAll = qrState.copy(settings = CameraSettings(scanAllCodes = true))

        assertEquals(
            SwipeOutcome.ShowQrFormats,
            mapper.map(direction = SwipeDirection.UP, state = qrState),
        )
        assertNull(mapper.map(direction = SwipeDirection.UP, state = scanningAll))
    }

    @Test
    fun swipes_whileRecording_doNothing() {
        val recording = photoState.copy(
            recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING),
        )

        SwipeDirection.entries.forEach { direction ->
            assertNull(mapper.map(direction = direction, state = recording))
        }
    }

    @Test
    fun swipes_duringTheSelfTimer_doNothing() {
        val counting = photoState.copy(capture = ViewfinderCaptureState(isSelfTimerRunning = true))

        SwipeDirection.entries.forEach { direction ->
            assertNull(mapper.map(direction = direction, state = counting))
        }
    }

    @Test
    fun sidewaysSwipes_withoutModeTabs_doNothing() {
        val withoutTabs = photoState.copy(showsCameraModeTabs = false)

        assertNull(mapper.map(direction = SwipeDirection.LEFT, state = withoutTabs))
    }
}
