package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import app.grapheneos.camera.domain.camera.usecase.ResolveAvailableModes
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.ui.viewfinder.screen.PreviewFrameHolder
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderChrome
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import com.google.zxing.BarcodeFormat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderCameraDelegateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val scope = TestScope(mainDispatcherRule.testDispatcher)

    private val chrome = mockk<ViewfinderChrome>(relaxed = true)
    private val previewFrames = mockk<PreviewFrameHolder>(relaxed = true)

    private val host = ViewfinderHost(
        previewTarget = mockk(relaxed = true),
        chrome = chrome,
        previewFrames = previewFrames,
    )
    private val session = mockk<CameraSession>(relaxed = true)
    private val resolveAvailableModes = mockk<ResolveAvailableModes>()

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
        every { session.isActive } returns true
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
        every { session.isActive } returns false

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
    }

    @Test
    fun selectLens_holdsTheCurrentFrameForTheTransition() {
        val delegate = createAttachedDelegate()
        delegate.selectLens(isQrMode = false, extensionMode = null)

        verify(exactly = 1) { previewFrames.holdCurrentFrame() }
    }

    @Test
    fun selectLens_forQrWithoutARearLens_scansWithTheFrontOne() {
        lensUnsupported(LensFacing.BACK)

        val delegate = createAttachedDelegate()
        val target = delegate.selectLens(isQrMode = true, extensionMode = null)

        assertEquals(LensFacing.FRONT, target?.qrLensFacing)
    }

    @Test
    fun switchLensFacing_toAnUnsupportedLens_keepsTheCurrentOne() {
        lensUnsupported(LensFacing.FRONT)

        val delegate = createAttachedDelegate()
        val switched = delegate.switchLensFacing(
            lensFacing = LensFacing.FRONT,
            extensionMode = null,
        )

        assertFalse(switched)
        assertEquals(LensFacing.BACK, lensFacing)
    }

    @Test
    fun switchLensFacing_toASupportedLens_takesIt() {
        val delegate = createAttachedDelegate()
        val switched = delegate.switchLensFacing(
            lensFacing = LensFacing.FRONT,
            extensionMode = null,
        )

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
    }

    @Test
    fun stepZoom_pastEitherEnd_stopsThere() {
        every { session.zoom } returns ZOOM

        val delegate = createAttachedDelegate()
        delegate.stepZoom(step = 100f)
        delegate.stepZoom(step = -100f)

        verifyOrder {
            session.setZoomRatio(ZOOM.maxZoomRatio)
            session.setZoomRatio(ZOOM.minZoomRatio)
        }
    }

    @Test
    fun stepZoom_outOfTheWideAngleCamera_stopsAtThePrimaryOne() {
        every { session.zoom } returns ZOOM.copy(zoomRatio = 0.6f, minZoomRatio = 0.5f)

        val delegate = createAttachedDelegate()
        delegate.stepZoom(step = 1f)

        verify(exactly = 1) { session.setZoomRatio(1f) }
    }

    @Test
    fun stepZoom_beforeTheZoomIsKnown_leavesTheZoomAlone() {
        every { session.zoom } returns null

        val delegate = createAttachedDelegate()
        delegate.stepZoom(step = 1f)

        verify(exactly = 0) { session.setZoomRatio(any()) }
    }

    @Test
    fun scaleZoom_scalesTheCurrentRatio() {
        every { session.zoom } returns ZOOM

        val delegate = createAttachedDelegate()
        delegate.scaleZoom(scaleFactor = 1.5f)

        verify(exactly = 1) { session.setZoomRatio(ZOOM.zoomRatio * 1.5f) }
    }

    @Test
    fun setExposureCompensation_isAppliedAndPublished() {
        stateHolder.update { it.copy(session = it.session.copy(exposure = EXPOSURE)) }

        val delegate = createAttachedDelegate()
        delegate.setExposureCompensation(compensationIndex = 5)

        verify(exactly = 1) { session.setExposureCompensationIndex(5) }
        assertEquals(5, stateHolder.state.value.session.exposure?.compensationIndex)
    }

    @Test
    fun onZoomStateChanged_publishesTheZoom() {
        every { session.zoom } returns ZOOM

        val delegate = createAttachedDelegate()
        delegate.onZoomStateChanged()

        assertEquals(ZOOM, stateHolder.state.value.session.zoom)
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
    fun onScreenDestroyed_keepsWhatTheCameraIsDoing() {
        every { session.lensFacing } returns LensFacing.FRONT

        val delegate = createAttachedDelegate()
        delegate.bindCamera(mockk(relaxed = true))
        delegate.applyFlashMode(FlashMode.AUTO)
        delegate.onScreenDestroyed()

        assertEquals(LensFacing.FRONT, stateHolder.state.value.session.lensFacing)
        assertEquals(FlashMode.AUTO, stateHolder.state.value.flashMode)
    }

    @Test
    fun onScreenDestroyed_forgetsTheScreenAndTheResultItWasShowing() {
        val delegate = createAttachedDelegate()
        delegate.showQrResult()
        delegate.onScreenDestroyed()

        verify(exactly = 1) { session.setPreviewTarget(null) }
        assertFalse(stateHolder.state.value.session.isQrResultShown)
        assertFalse(delegate.beginBind(forced = true))
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
    fun bindCamera_outsideVideoMode_doesNotAskWhetherVideoCanBeStabilized() {
        val delegate = createAttachedDelegate()
        delegate.bindCamera(mockk(relaxed = true) { every { isVideoMode } returns false })

        verify(exactly = 0) { session.canApplyVideoStabilization() }
        assertFalse(stateHolder.state.value.session.canApplyVideoStabilization)
    }

    @Test
    fun bindCamera_inVideoMode_publishesWhetherVideoCanBeStabilized() {
        every { session.canApplyVideoStabilization() } returns true

        val delegate = createAttachedDelegate()
        delegate.bindCamera(mockk(relaxed = true) { every { isVideoMode } returns true })

        assertTrue(stateHolder.state.value.session.canApplyVideoStabilization)
    }

    @Test
    fun bindCamera_leavesTheTorchOff() {
        every { session.isTorchOn } returns true

        val delegate = createAttachedDelegate()
        delegate.toggleTorch()
        delegate.bindCamera(mockk(relaxed = true))

        assertFalse(stateHolder.state.value.session.isTorchOn)
    }

    @Test
    fun showQrResult_showsItOnceAndStopsTheCamera() {
        val delegate = createAttachedDelegate()

        assertTrue(delegate.showQrResult())
        assertFalse(delegate.showQrResult())

        verify(exactly = 1) { session.unbind() }
        assertTrue(stateHolder.state.value.session.isQrResultShown)
    }

    @Test
    fun dismissQrResult_letsTheNextCodeBeShown() {
        val delegate = createAttachedDelegate()

        delegate.showQrResult()
        delegate.dismissQrResult()
        delegate.showQrResult()

        verify(exactly = 2) { session.unbind() }
    }

    @Test
    fun barcodeFormats_whenTheSettingsChangeThem_reachTheSession() {
        createAttachedDelegate()

        stateHolder.update { it.copy(settings = CameraSettings(scanAllCodes = true)) }

        verify(exactly = 1) { session.setBarcodeFormats(BarcodeFormat.entries.toSet()) }
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
            session = session,
            entryPoint = CameraEntryPoint(
                isSecureSession = false,
                isCaptureSession = false,
                isVideoOnlySession = false,
                requiresVideoModeOnly = false,
                allowsQrScanning = true,
                showsCameraModeTabs = showsCameraModeTabs,
            ),
            resolveAvailableModes = resolveAvailableModes,
            mainDispatcher = mainDispatcherRule.testDispatcher,
        )

        delegate.bind(
            scope = scope,
            stateHolder = stateHolder,
        )

        delegate.onScreenCreated(host)

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
