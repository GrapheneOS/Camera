package app.grapheneos.camera.ui.viewfinder.screen.model

import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderStateTest {

    @Test
    fun photoMode_isNeitherQrNorVideo() {
        val state = state(mode = CameraMode.CAMERA)

        assertTrue(state.isInPhotoMode())
        assertFalse(state.isQrMode())
        assertFalse(state.isVideoMode())
    }

    @Test
    fun qrMode_isNotPhotoMode() {
        val state = state(mode = CameraMode.QR_SCAN)

        assertTrue(state.isQrMode())
        assertFalse(state.isInPhotoMode())
    }

    @Test
    fun isVideoMode_inAVideoOnlyEntryPoint_holdsForEveryMode() {
        val state = state(mode = CameraMode.CAMERA, requiresVideoModeOnly = true)

        assertTrue(state.isVideoMode())
        assertFalse(state.isInPhotoMode())
    }

    @Test
    fun aspectRatio_inPhotoMode_isTheStoredOne() {
        val state = state(
            mode = CameraMode.CAMERA,
            settings = CameraSettings(aspectRatio = AspectRatio.RATIO_16_9),
        )

        assertEquals(AspectRatio.RATIO_16_9, state.aspectRatio())
    }

    @Test
    fun aspectRatio_inVideoMode_isAlwaysWide() {
        val state = state(
            mode = CameraMode.VIDEO,
            settings = CameraSettings(aspectRatio = AspectRatio.RATIO_4_3),
        )

        assertEquals(AspectRatio.RATIO_16_9, state.aspectRatio())
    }

    @Test
    fun aspectRatio_inQrMode_isAlwaysFourByThree() {
        val state = state(
            mode = CameraMode.QR_SCAN,
            settings = CameraSettings(aspectRatio = AspectRatio.RATIO_16_9),
        )

        assertEquals(AspectRatio.RATIO_4_3, state.aspectRatio())
    }

    @Test
    fun selfIlluminate_needsTheSettingAndTheFrontLens() {
        val enabled = ModeSettings(selfIllumination = true)
        val front = ViewfinderSessionState(lensFacing = CameraSelector.LENS_FACING_FRONT)
        val back = ViewfinderSessionState(lensFacing = CameraSelector.LENS_FACING_BACK)

        assertTrue(state(modeSettings = enabled, session = front).selfIlluminate())
        assertFalse(state(modeSettings = enabled, session = back).selfIlluminate())
        assertFalse(state(session = front).selfIlluminate())
    }

    private fun state(
        mode: CameraMode = CameraMode.CAMERA,
        requiresVideoModeOnly: Boolean = false,
        settings: CameraSettings = CameraSettings(),
        modeSettings: ModeSettings = ModeSettings(),
        session: ViewfinderSessionState = ViewfinderSessionState(),
    ): ViewfinderState {
        return ViewfinderState(
            mode = mode,
            requiresVideoModeOnly = requiresVideoModeOnly,
            settings = settings,
            modeSettings = modeSettings,
            session = session,
        )
    }
}
