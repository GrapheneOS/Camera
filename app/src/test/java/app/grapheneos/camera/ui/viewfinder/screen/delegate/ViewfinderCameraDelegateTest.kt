package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.view.Surface
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.data.camera.model.CameraSessionEvent
import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.sound.model.CameraSound
import app.grapheneos.camera.domain.camera.usecase.ResolveAvailableModes
import app.grapheneos.camera.domain.camera.usecase.ResolveDroppedVideoQuality
import app.grapheneos.camera.domain.sound.usecase.PlayCameraSound
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.testutil.viewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.PreviewFrameHolder
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.model.ThumbnailSize
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCameraEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import com.google.zxing.BarcodeFormat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import org.junit.After
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

    private val previewFrames = mockk<PreviewFrameHolder>(relaxed = true)

    private val host = ViewfinderHost(
        previewTarget = mockk(relaxed = true),
        previewFrames = previewFrames,
        thumbnailSize = ThumbnailSize(width = 1, height = 1),
    )
    private val session = mockk<CameraSession>(relaxed = true)
    private val resolveAvailableModes = mockk<ResolveAvailableModes>()
    private val resolveDroppedVideoQuality = mockk<ResolveDroppedVideoQuality>()
    private val playCameraSound = mockk<PlayCameraSound>(relaxed = true)
    private val sessionEvents = MutableSharedFlow<CameraSessionEvent>(extraBufferCapacity = 8)
    private val effects = mutableListOf<Effect>()

    private val stateHolder = viewfinderStateHolder(mode = CameraMode.CAMERA)

    private var lensFacing = LensFacing.BACK

    @Before
    fun setUp() {
        every { session.lensFacing } answers { lensFacing }
        every { session.lensFacing = any() } answers { lensFacing = firstArg() }
        every {
            session.isLensFacingSupported(lensFacing = any(), extensionMode = any())
        } returns true
        every { session.isActive } returns true
        every { session.events } returns sessionEvents
    }

    @After
    fun cancelScope() {
        scope.cancel()
    }

    @Test
    fun canBeginBind_whileAlreadyBoundAndNotForced_refusesTheBind() {
        val delegate = createAttachedDelegate()

        assertFalse(delegate.canBeginBind(forced = false))
    }

    @Test
    fun canBeginBind_withoutACameraProvider_refusesTheBindEvenWhenForced() {
        every { session.cameraProvider } returns null

        val delegate = createAttachedDelegate()

        assertFalse(delegate.canBeginBind(forced = true))
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
    fun focusAt_focusesForTheChosenTimeoutAndShowsItWithTheSoundOnlyForPhotos() {
        val delegate = createAttachedDelegate()
        stateHolder.update {
            it.copy(settings = CameraSettings(focusTimeoutSeconds = FOCUS_TIMEOUT_SECONDS))
        }

        delegate.focusAt(x = 10f, y = 20f)
        stateHolder.update { it.copy(mode = CameraMode.VIDEO) }
        delegate.focusAt(x = 10f, y = 20f)

        verify(exactly = 2) {
            session.startFocusAndMetering(
                x = 10f,
                y = 20f,
                autoCancelSeconds = FOCUS_TIMEOUT_SECONDS,
            )
        }
        assertEquals(
            listOf(
                Effect.Preview.ShowFocus(x = 10f, y = 20f),
                Effect.Preview.ShowFocus(x = 10f, y = 20f),
            ),
            effects,
        )
        coVerify(exactly = 1) { playCameraSound(CameraSound.FOCUS_START) }
    }

    @Test
    fun focusAndZoom_inQrMode_areLeftAlone() {
        every { session.zoom } returns ZOOM
        val delegate = createAttachedDelegate()
        stateHolder.update { it.copy(mode = CameraMode.QR_SCAN) }

        delegate.focusAt(x = 10f, y = 20f)
        delegate.cancelFocus()
        delegate.stepZoom(step = 1f)

        verify(exactly = 0) {
            session.startFocusAndMetering(x = any(), y = any(), autoCancelSeconds = any())
        }
        verify(exactly = 0) { session.cancelFocusAndMetering() }
        verify(exactly = 0) { session.setZoomRatio(any()) }
        assertTrue(effects.isEmpty())
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
    fun zoomStateLoaded_publishesTheZoomWithoutShowingItsPanel() {
        every { session.zoom } returns ZOOM
        createAttachedDelegate()

        sessionEvents.tryEmit(CameraSessionEvent.ZoomStateLoaded)

        assertEquals(ZOOM, stateHolder.state.value.session.zoom)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun zoomStateChanged_publishesTheZoomAndShowsItsPanel() {
        every { session.zoom } returns ZOOM
        createAttachedDelegate()

        sessionEvents.tryEmit(CameraSessionEvent.ZoomStateChanged)

        assertEquals(ZOOM, stateHolder.state.value.session.zoom)
        assertEquals(listOf(Effect.Panel.ShowZoom), effects)
    }

    @Test
    fun providerReady_isHandedOn() {
        val delegate = createAttachedDelegate()
        val events = mutableListOf<ViewfinderCameraEvent>()
        scope.launch(mainDispatcherRule.testDispatcher) {
            delegate.cameraEvents.collect { events += it }
        }

        sessionEvents.tryEmit(CameraSessionEvent.ProviderReady(forced = true))

        assertEquals(listOf(ViewfinderCameraEvent.ProviderReady(forced = true)), events)
    }

    @Test
    fun providerAndExtensionFailures_saySo() {
        createAttachedDelegate()

        sessionEvents.tryEmit(CameraSessionEvent.CameraProviderUnavailable)
        sessionEvents.tryEmit(CameraSessionEvent.ExtensionsUnavailable)

        assertEquals(
            listOf(
                Effect.ShowMessage(R.string.camera_provider_init_failure),
                Effect.ShowMessage(R.string.extensions_manager_init_failure),
            ),
            effects,
        )
    }

    @Test
    fun featuresSelected_saysWhenTheRequestedQualityWasDropped() {
        every {
            resolveDroppedVideoQuality(
                lensFacing = any(),
                requestedQualityFeature = any(),
                selected = any(),
            )
        } returnsMany listOf(VideoQuality.UHD, null)
        createAttachedDelegate()

        sessionEvents.tryEmit(featuresSelected())
        sessionEvents.tryEmit(featuresSelected())

        assertEquals(listOf(Effect.ShowVideoQualityUnsupported(VideoQuality.UHD)), effects)
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
        sessionEvents.tryEmit(CameraSessionEvent.QrCodeScanned(text = QR_TEXT))
        delegate.onScreenDestroyed()

        verify(exactly = 1) { session.setPreviewTarget(null) }
        assertFalse(stateHolder.state.value.session.isQrResultShown)
        assertFalse(delegate.canBeginBind(forced = true))
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
    fun qrCodeScanned_showsTheResultOnceAndStopsTheCamera() {
        createAttachedDelegate()

        sessionEvents.tryEmit(CameraSessionEvent.QrCodeScanned(text = QR_TEXT))
        sessionEvents.tryEmit(CameraSessionEvent.QrCodeScanned(text = QR_TEXT))

        verify(exactly = 1) { session.unbind() }
        assertTrue(stateHolder.state.value.session.isQrResultShown)
        assertEquals(listOf(Effect.ShowQrResult(QR_TEXT)), effects)
    }

    @Test
    fun dismissQrResult_letsTheNextCodeBeShown() {
        val delegate = createAttachedDelegate()

        sessionEvents.tryEmit(CameraSessionEvent.QrCodeScanned(text = QR_TEXT))
        delegate.dismissQrResult()
        sessionEvents.tryEmit(CameraSessionEvent.QrCodeScanned(text = QR_TEXT))

        verify(exactly = 2) { session.unbind() }
    }

    @Test
    fun deviceOrientation_reachesTheSessionOncePerChange() {
        createAttachedDelegate()

        stateHolder.update { it.copy(deviceOrientation = DeviceOrientation.DEGREES_90) }
        stateHolder.update { it.copy(settings = CameraSettings(scanAllCodes = true)) }

        verify(exactly = 1) { session.setCaptureOrientation(DeviceOrientation.DEGREES_90) }
    }

    @Test
    fun previewRotation_reachesTheSession() {
        createAttachedDelegate().setPreviewRotation(Surface.ROTATION_270)

        verify(exactly = 1) { session.setPreviewRotation(Surface.ROTATION_270) }
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

    private fun featuresSelected(): CameraSessionEvent.FeaturesSelected {
        return CameraSessionEvent.FeaturesSelected(
            boundLensFacing = LensFacing.BACK,
            requested = emptyList(),
            qualityFeature = null,
            selected = emptySet(),
        )
    }

    private fun createAttachedDelegate(
        showsCameraModeTabs: Boolean = true,
    ): ViewfinderCameraDelegate {
        val delegate = ViewfinderCameraDelegateImpl(
            session = session,
            entryPoint = cameraEntryPoint(showsCameraModeTabs = showsCameraModeTabs),
            resolveAvailableModes = resolveAvailableModes,
            resolveDroppedVideoQuality = resolveDroppedVideoQuality,
            playCameraSound = playCameraSound,
            mainDispatcher = mainDispatcherRule.testDispatcher,
        )

        delegate.bind(
            scope = scope,
            stateHolder = stateHolder,
        )
        scope.launch(mainDispatcherRule.testDispatcher) {
            stateHolder.effects.collect { effects += it }
        }

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
        const val QR_TEXT = "https://grapheneos.org"
        const val FOCUS_TIMEOUT_SECONDS = 3L
    }
}
