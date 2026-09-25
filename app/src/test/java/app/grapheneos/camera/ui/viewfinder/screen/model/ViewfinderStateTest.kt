package app.grapheneos.camera.ui.viewfinder.screen.model

import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import com.google.zxing.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderStateTest {

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
        val front = ViewfinderSessionState(lensFacing = LensFacing.FRONT)
        val back = ViewfinderSessionState(lensFacing = LensFacing.BACK)

        assertTrue(state(modeSettings = enabled, session = front).selfIlluminate())
        assertFalse(state(modeSettings = enabled, session = back).selfIlluminate())
        assertFalse(state(session = front).selfIlluminate())
    }

    @Test
    fun barcodeFormats_scanningAllCodes_areEveryFormat() {
        val state = state(
            mode = CameraMode.QR_SCAN,
            settings = CameraSettings(scanAllCodes = true),
        )

        assertEquals(BarcodeFormat.entries.toSet(), state.barcodeFormats())
    }

    @Test
    fun barcodeFormats_otherwise_areTheEnabledOnes() {
        val state = state(
            mode = CameraMode.QR_SCAN,
            settings = CameraSettings(
                enabledBarcodeFormats = setOf(BarcodeFormat.AZTEC.name, BarcodeFormat.QR_CODE.name),
            ),
        )

        assertEquals(setOf(BarcodeFormat.AZTEC, BarcodeFormat.QR_CODE), state.barcodeFormats())
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
