package app.grapheneos.camera.ui.viewfinder.screen.model.state

import android.net.Uri
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import com.google.zxing.BarcodeFormat
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderStateTest {

    @Test
    fun isVideoMode_followsTheSelectedMode() {
        for (mode in CameraMode.entries) {
            for (requiresVideoModeOnly in listOf(false, true)) {
                val state = state(mode = mode, requiresVideoModeOnly = requiresVideoModeOnly)

                assertEquals(mode == CameraMode.VIDEO, state.isVideoMode())
                assertEquals(
                    mode != CameraMode.VIDEO && mode != CameraMode.QR_SCAN,
                    state.isPhotoMode(),
                )
            }
        }
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
    fun includesLocation_onlyWhileGeoTaggingIsOnAndPermitted() {
        val on = state(settings = CameraSettings(geoTagging = true))

        assertTrue(on.includesLocation())
        assertFalse(on.copy(missingPermissions = setOf(AppPermission.LOCATION)).includesLocation())
        assertFalse(state(settings = CameraSettings(geoTagging = false)).includesLocation())
    }

    @Test
    fun isReviewingRecordedVideo_inAVideoCaptureSession_coversTheSaveBeforeThePreview() {
        val session = state(mode = CameraMode.VIDEO, requiresVideoModeOnly = true)
            .copy(isCaptureSession = true)

        assertFalse(session.isReviewingRecordedVideo())
        assertTrue(
            session.copy(recording = ViewfinderRecordingState(isSaving = true))
                .isReviewingRecordedVideo(),
        )
        assertTrue(
            session.copy(
                capture = ViewfinderCaptureState(
                    result = ViewfinderCaptureResult.Video(uri = Uri.EMPTY, image = null),
                ),
            )
                .isReviewingRecordedVideo(),
        )
    }

    @Test
    fun isReviewingRecordedVideo_outsideACaptureSession_neverHolds() {
        val saving = state(mode = CameraMode.VIDEO, requiresVideoModeOnly = true)
            .copy(recording = ViewfinderRecordingState(isSaving = true))

        assertFalse(saving.isReviewingRecordedVideo())
    }

    @Test
    fun selfIlluminate_needsTheSettingAndTheFrontLens() {
        val enabled = ModeSettings(selfIllumination = true)
        val front = ViewfinderSessionState(lensFacing = LensFacing.FRONT)
        val back = ViewfinderSessionState(lensFacing = LensFacing.BACK)

        assertTrue(state(modeSettings = enabled, session = front).selfIlluminates())
        assertFalse(state(modeSettings = enabled, session = back).selfIlluminates())
        assertFalse(state(session = front).selfIlluminates())
    }

    @Test
    fun isSettingsPanelAvailable_outsideRecordingsTheSelfTimerAndQrMode() {
        val photo = state()

        assertTrue(photo.isSettingsPanelAvailable())
        assertFalse(
            photo
                .copy(recording = ViewfinderRecordingState(phase = RecordingPhase.STARTING))
                .isSettingsPanelAvailable(),
        )
        assertFalse(
            photo
                .copy(capture = ViewfinderCaptureState(selfTimerRemaining = 3.seconds))
                .isSettingsPanelAvailable(),
        )
        assertFalse(state(mode = CameraMode.QR_SCAN).isSettingsPanelAvailable())
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
