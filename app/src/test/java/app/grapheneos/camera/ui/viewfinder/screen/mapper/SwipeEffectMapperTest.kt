package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.SwipeDirection
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderRecordingState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SwipeEffectMapperTest {

    private val mapper = SwipeEffectMapperImpl()

    private val photoState = ViewfinderState(
        mode = CameraMode.CAMERA,
        requiresVideoModeOnly = false,
        showsCameraModeTabs = true,
    )

    @Test
    fun sidewaysSwipes_moveAcrossTheModeTabs() {
        assertEquals(
            Effect.ModeTab.SelectAdjacent(offset = 1),
            mapper.map(direction = SwipeDirection.LEFT, state = photoState),
        )
        assertEquals(
            Effect.ModeTab.SelectAdjacent(offset = -1),
            mapper.map(direction = SwipeDirection.RIGHT, state = photoState),
        )
    }

    @Test
    fun verticalSwipes_openAndCloseTheSettings() {
        assertEquals(
            Effect.Settings.OpenSheet,
            mapper.map(direction = SwipeDirection.DOWN, state = photoState),
        )
        assertEquals(
            Effect.Settings.CloseSheet,
            mapper.map(direction = SwipeDirection.UP, state = photoState),
        )
    }

    @Test
    fun swipeDown_inQrMode_offersTheFormatsUnlessAllAreScanned() {
        val qrState = photoState.copy(mode = CameraMode.QR_SCAN)
        val scanningAll = qrState.copy(settings = CameraSettings(scanAllCodes = true))

        assertEquals(
            Effect.Settings.ShowQrFormats,
            mapper.map(direction = SwipeDirection.DOWN, state = qrState),
        )
        assertNull(mapper.map(direction = SwipeDirection.DOWN, state = scanningAll))
    }

    @Test
    fun swipes_whileRecording_stillOpenTheSettingsButKeepTheMode() {
        val recording = photoState.copy(
            recording = ViewfinderRecordingState(phase = RecordingPhase.RECORDING),
        )

        assertNull(mapper.map(direction = SwipeDirection.LEFT, state = recording))
        assertNull(mapper.map(direction = SwipeDirection.UP, state = recording))
        assertEquals(
            Effect.Settings.OpenSheet,
            mapper.map(direction = SwipeDirection.DOWN, state = recording),
        )
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
