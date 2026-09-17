package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.camera.session.CameraSessionEnvironment
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import app.grapheneos.camera.domain.camera.usecase.ResolveAvailableModes
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderChrome
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderSessionState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderCameraDelegateTest {

    private val environment = mockk<CameraSessionEnvironment>(relaxed = true)
    private val chrome = mockk<ViewfinderChrome>(relaxed = true)
    private val session = mockk<CameraSession>(relaxed = true)
    private val resolveAvailableModes = mockk<ResolveAvailableModes>()

    private val emitted = mutableListOf<ViewfinderScreenEffect>()

    private val stateHolder = ViewfinderStateHolder(
        initial = ViewfinderState(mode = CameraMode.CAMERA, requiresVideoModeOnly = false),
        render = { ViewfinderUiState() },
    )

    private var lensFacing = LensFacing.BACK

    @Before
    fun setUp() {
        every { session.lensFacing } answers { lensFacing }
        every { session.lensFacing = any() } answers { lensFacing = firstArg() }
        every {
            session.isLensFacingSupported(lensFacing = any(), extensionMode = any())
        } returns true
        every { environment.isSessionActive } returns true
    }

    @Test
    fun beginBind_whileAlreadyBoundAndNotForced_leavesTheCameraAlone() {
        val delegate = createAttachedDelegate()
        val started = delegate.beginBind(forced = false)

        assertFalse(started)
        verify(exactly = 0) { chrome.cancelPendingCapture() }
    }

    @Test
    fun beginBind_withoutACameraProvider_leavesTheCameraAloneEvenWhenForced() {
        every { session.cameraProvider } returns null

        val delegate = createAttachedDelegate()
        val started = delegate.beginBind(forced = true)

        assertFalse(started)
    }

    @Test
    fun selectLens_whileTheSessionIsInactive_selectsNothing() {
        every { environment.isSessionActive } returns false

        val delegate = createAttachedDelegate()
        val target = delegate.selectLens(isQrMode = false, extensionMode = null)

        assertNull(target)
        verify(exactly = 0) { session.selectLensFacing(any()) }
    }

    @Test
    fun selectLens_whenTheCurrentLensIsUnsupported_silentlyTakesTheOtherOne() {
        lensUnsupported(LensFacing.BACK)

        val delegate = createAttachedDelegate()
        delegate.selectLens(isQrMode = false, extensionMode = null)

        verify(exactly = 1) { session.selectLensFacing(LensFacing.FRONT) }
        assertTrue(emitted.isEmpty())
    }

    @Test
    fun selectLens_forQrWithoutARearLens_scansWithTheFrontOneAndSaysSo() {
        lensUnsupported(LensFacing.BACK)

        val delegate = createAttachedDelegate()
        val target = delegate.selectLens(isQrMode = true, extensionMode = null)

        assertEquals(LensFacing.FRONT, target?.qrLensFacing)
        assertEquals(
            listOf(ViewfinderScreenEffect.ShowMessage(R.string.qr_rear_camera_unavailable)),
            emitted,
        )
    }

    @Test
    fun toggleLensFacing_toAnUnsupportedLens_revertsAndSaysSo() {
        lensUnsupported(LensFacing.FRONT)

        val delegate = createAttachedDelegate()
        val switched = delegate.toggleLensFacing(extensionMode = null)

        assertFalse(switched)
        assertEquals(LensFacing.BACK, lensFacing)
        assertEquals(
            listOf(ViewfinderScreenEffect.ShowMessage(R.string.front_camera_unavailable)),
            emitted,
        )
    }

    @Test
    fun toggleLensFacing_toASupportedLens_keepsIt() {
        val delegate = createAttachedDelegate()
        val switched = delegate.toggleLensFacing(extensionMode = null)

        assertTrue(switched)
        assertEquals(LensFacing.FRONT, lensFacing)
    }

    @Test
    fun announceBind_withoutModeTabs_probesNoExtensions() {
        val delegate = createAttachedDelegate(showsCameraModeTabs = false)

        delegate.announceBind()

        verify(exactly = 0) { session.probeUnknownExtensions(onRestart = any(), onSettled = any()) }
    }

    @Test
    fun announceBind_publishesTheModesTheCameraOffers() {
        val modes = setOf(CameraMode.CAMERA, CameraMode.VIDEO)
        every { session.probeUnknownExtensions(onRestart = any(), onSettled = any()) } answers {
            secondArg<() -> Unit>().invoke()
        }
        every { resolveAvailableModes(allowsQrScanning = any(), extensionsAvailable = any()) }
            .returns(modes)

        val delegate = createAttachedDelegate()
        delegate.announceBind()

        assertEquals(modes, stateHolder.state.value.session.availableModes)
    }

    @Test
    fun announceBind_publishesTheBoundCamerasZoomAndExposure() {
        every { session.zoom } returns ZOOM
        every { session.exposure } returns EXPOSURE

        val delegate = createAttachedDelegate(showsCameraModeTabs = false)
        delegate.announceBind()

        assertEquals(ZOOM, stateHolder.state.value.session.zoom)
        assertEquals(EXPOSURE, stateHolder.state.value.session.exposure)
        assertEquals(listOf(ViewfinderScreenEffect.HideZoomPanel), emitted)
    }

    @Test
    fun beginBind_hidesTheExposurePanel() {
        every { session.camera } returns null

        val delegate = createAttachedDelegate()
        delegate.beginBind(forced = true)

        assertEquals(listOf(ViewfinderScreenEffect.HideExposurePanel), emitted)
    }

    @Test
    fun bindCamera_forgetsTheOldCamerasExposure() {
        stateHolder.update { it.copy(session = it.session.copy(exposure = EXPOSURE)) }

        val delegate = createAttachedDelegate()
        delegate.bindCamera(mockk(relaxed = true))

        assertNull(stateHolder.state.value.session.exposure)
    }

    @Test
    fun onZoomStateChanged_publishesTheZoomAndShowsItsPanel() {
        every { session.zoom } returns ZOOM

        val delegate = createAttachedDelegate()
        delegate.onZoomStateChanged()

        assertEquals(ZOOM, stateHolder.state.value.session.zoom)
        assertEquals(listOf(ViewfinderScreenEffect.ShowZoomPanel), emitted)
    }

    @Test
    fun refreshVideoQualities_publishesWhatTheCameraRecordsAt() {
        val qualities = listOf(VideoQuality.UHD, VideoQuality.FHD)
        every { session.supportedVideoQualities() } returns qualities

        val delegate = createAttachedDelegate()
        delegate.refreshVideoQualities()

        assertEquals(qualities, stateHolder.state.value.session.videoQualities)
    }

    @Test
    fun bindCamera_keepsTheModesAndPublishesWhatTheNewCameraSupports() {
        val modes = setOf(CameraMode.CAMERA)
        every { session.isZslSupported } returns true
        every { session.sensorOrientationDegrees } returns SENSOR_ORIENTATION
        stateHolder.update { it.copy(session = it.session.copy(availableModes = modes)) }

        val delegate = createAttachedDelegate()
        delegate.bindCamera(mockk(relaxed = true))

        val session = stateHolder.state.value.session
        assertEquals(modes, session.availableModes)
        assertTrue(session.isZslSupported)
        assertEquals(SENSOR_ORIENTATION, session.sensorOrientationDegrees)
    }

    @Test
    fun detach_forgetsTheSession() {
        every { session.isFlashAvailable } returns true

        val delegate = createAttachedDelegate()
        delegate.detach()

        assertEquals(ViewfinderSessionState(), stateHolder.state.value.session)
    }

    @Test
    fun applyFlashMode_isRememberedAcrossDetach() {
        val delegate = createAttachedDelegate()

        delegate.applyFlashMode(FlashMode.AUTO)
        delegate.detach()

        assertEquals(FlashMode.AUTO, stateHolder.state.value.flashMode)
    }

    @Test
    fun toggleTorch_recordsWhatTheSessionReportsAfterwards() {
        var torchOn = false
        every { session.isTorchOn } answers { torchOn }
        every { session.toggleTorchState() } answers { torchOn = !torchOn }

        val delegate = createAttachedDelegate()
        delegate.toggleTorch()

        assertTrue(stateHolder.state.value.session.isTorchOn)
    }

    @Test
    fun bindCamera_leavesTheTorchOff() {
        every { session.isTorchOn } returns true

        val delegate = createAttachedDelegate()
        delegate.toggleTorch()
        delegate.bindCamera(mockk(relaxed = true))

        assertFalse(stateHolder.state.value.session.isTorchOn)
    }

    private fun lensUnsupported(facing: LensFacing) {
        every {
            session.isLensFacingSupported(
                lensFacing = facing,
                extensionMode = any(),
            )
        } returns false
    }

    private fun createAttachedDelegate(
        showsCameraModeTabs: Boolean = true,
    ): ViewfinderCameraDelegate {
        val delegate = ViewfinderCameraDelegateImpl(
            entryPoint = CameraEntryPoint(
                isSecureSession = false,
                isCaptureSession = false,
                isVideoOnlySession = false,
                requiresVideoModeOnly = false,
                allowsQrScanning = true,
                showsCameraModeTabs = showsCameraModeTabs,
            ),
            resolveAvailableModes = resolveAvailableModes,
        )

        delegate.bind(stateHolder)

        delegate.attach(
            environment = environment,
            chrome = chrome,
            session = session,
            emitEffect = { emitted += it },
        )

        return delegate
    }

    private companion object {
        val ZOOM = CameraZoom(
            zoomRatio = 2f,
            linearZoom = 0.5f,
            minZoomRatio = 1f,
            maxZoomRatio = 10f,
        )

        val EXPOSURE = CameraExposure(
            compensationIndex = 1,
            compensationRange = -12..12,
        )

        const val SENSOR_ORIENTATION = 90
    }
}
