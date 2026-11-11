package app.grapheneos.camera.data.camera.session

import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.SessionConfig
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import app.grapheneos.camera.data.camera.mapper.CameraFlashMapperImpl
import app.grapheneos.camera.data.camera.mapper.CameraXConstantsMapperImpl
import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.data.camera.model.CameraSessionPlan
import app.grapheneos.camera.data.camera.model.CameraZoomEvent
import app.grapheneos.camera.data.camera.model.FeatureGroupRequest
import app.grapheneos.camera.data.camera.model.LensFeatures
import app.grapheneos.camera.data.camera.model.PreviewTarget
import app.grapheneos.camera.data.camera.model.QrBindSettings
import app.grapheneos.camera.data.camera.model.VideoBindSettings
import app.grapheneos.camera.data.camera.model.VideoFeatureGroup
import app.grapheneos.camera.data.camera.repository.CameraProviderSource
import app.grapheneos.camera.data.camera.session.ZoomControl.Restoration
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.data.core.model.VideoQuality
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import io.mockk.verifySequence
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CameraSessionTest {

    private val lifecycleOwner = TestLifecycleOwner()

    private val zoomStates = MutableLiveData<ZoomState>(mockk(relaxed = true))

    private val cameraControl = mockk<CameraControl>(relaxed = true)
    private val focusControl = mockk<FocusControl>(relaxed = true)
    private val zoomControl = mockk<ZoomControl>(relaxed = true)
    private val exposure = MutableStateFlow<CameraExposure?>(
        CameraExposure(
            compensationIndex = 0,
            compensationRange = -12..12,
            compensationStep = 1f / 6f,
            isCompensationSupported = true,
        ),
    )
    private val exposureControl = mockk<ExposureControl>(relaxed = true) {
        every { exposure } returns this@CameraSessionTest.exposure
    }

    private val sceneLightControl = mockk<SceneLightControl>(relaxed = true)

    private val cameraInfo = mockk<CameraInfo>(relaxed = true) {
        every { zoomState } returns zoomStates
        every { hasFlashUnit() } returns true
    }

    private val camera = mockk<Camera>(relaxed = true) {
        every { cameraControl } returns this@CameraSessionTest.cameraControl
        every { cameraInfo } returns this@CameraSessionTest.cameraInfo
    }

    private val provider = mockk<ProcessCameraProvider>(relaxed = true) {
        every {
            bindToLifecycle(any<LifecycleOwner>(), any<CameraSelector>(), any<SessionConfig>())
        } returns camera
    }

    private val cameraProviderSource = mockk<CameraProviderSource> {
        every { acquireProvider(any()) } answers {
            firstArg<(ProcessCameraProvider?) -> Unit>().invoke(provider)
        }
        every { acquireExtensionsManager(any(), any()) } just runs
    }

    private val cameraFeatureSupport = mockk<CameraFeatureSupport> {
        every { features(any()) } returns LensFeatures()
    }

    private val featureGroupRequestResolver = mockk<FeatureGroupRequestResolver> {
        every {
            resolve(any(), any(), any(), any(), any(), any(), any())
        } returns VideoFeatureGroup(request = FeatureGroupRequest.Unused)
    }

    private val cameraSessionPlanFactory = mockk<CameraSessionPlanFactory> {
        every { create(any()) } answers {
            CameraSessionPlan(
                videoCapture = null,
                imageCapture = null,
                preview = mockk(relaxed = true),
                preferredFeatures = emptyList(),
            )
        }
        every { createQrAnalysis() } answers { mockk(relaxed = true) }
    }

    @Test
    fun bind_inQrMode_refocusesTheScanAreaEveryTwoSecondsWhileResumed() {
        runTest {
            boundSession(QR_SETTINGS)

            advanceTimeBy(2.seconds - 1.milliseconds)
            verify(exactly = 0) { cameraControl.startFocusAndMetering(any()) }

            advanceTimeBy(1.milliseconds)
            runCurrent()
            verify(exactly = 1) { cameraControl.startFocusAndMetering(any()) }

            advanceTimeBy(2.seconds)
            runCurrent()
            verify(exactly = 2) { cameraControl.startFocusAndMetering(any()) }
        }
    }

    @Test
    fun bind_inQrMode_stopsRefocusingOnceThePreviewIsNoLongerResumed() {
        runTest {
            boundSession(QR_SETTINGS)

            advanceTimeBy(2.seconds)
            runCurrent()
            lifecycleOwner.registry.currentState = Lifecycle.State.STARTED
            advanceTimeBy(2.seconds)
            runCurrent()
            lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
            advanceTimeBy(10.seconds)
            runCurrent()

            verify(exactly = 1) { cameraControl.startFocusAndMetering(any()) }
        }
    }

    @Test
    fun bind_again_restartsTheQrRefocusInterval() {
        runTest {
            val session = boundSession(QR_SETTINGS)

            advanceTimeBy(1.5.seconds)
            session.bind(QR_SETTINGS)
            advanceTimeBy(1.seconds)
            runCurrent()
            verify(exactly = 0) { cameraControl.startFocusAndMetering(any()) }

            advanceTimeBy(1.seconds)
            runCurrent()
            verify(exactly = 1) { cameraControl.startFocusAndMetering(any()) }
        }
    }

    @Test
    fun bind_toAPhotoMode_doesNotRefocusTheQrScanArea() {
        runTest {
            boundSession(PHOTO_SETTINGS)

            advanceTimeBy(10.seconds)
            runCurrent()

            verify(exactly = 0) { cameraControl.startFocusAndMetering(any()) }
        }
    }

    @Test
    fun previewGoingAway_stopsTheQrRefocus() {
        runTest {
            val session = boundSession(QR_SETTINGS)

            session.setPreviewTarget(null)
            advanceTimeBy(10.seconds)
            runCurrent()

            verify(exactly = 0) { cameraControl.startFocusAndMetering(any()) }
        }
    }

    @Test
    fun bind_attachesTheControlsToTheBoundCamera() {
        runTest {
            every { provider.hasCamera(any()) } returns true
            boundSession(PHOTO_SETTINGS)

            verify(exactly = 1) {
                focusControl.attach(control = cameraControl, meteringPointFactory = any())
            }
            verify(exactly = 1) {
                zoomControl.attach(
                    camera = camera,
                    lensFacing = LensFacing.BACK,
                    restoration = Restoration.BEFORE_PREVIEW,
                )
            }
        }
    }

    @Test
    fun unbind_detachesTheControls() {
        runTest {
            val session = boundSession(PHOTO_SETTINGS)
            clearMocks(focusControl, zoomControl, answers = false)

            session.unbind()

            verify(exactly = 1) { focusControl.detach() }
            verify(exactly = 1) { zoomControl.detach() }
        }
    }

    @Test
    fun previewGoingAway_detachesTheControls() {
        runTest {
            val session = boundSession(PHOTO_SETTINGS)
            clearMocks(focusControl, zoomControl, answers = false)

            session.setPreviewTarget(null)

            verify(exactly = 1) { focusControl.detach() }
            verify(exactly = 1) { zoomControl.detach() }
        }
    }

    @Test
    fun photoExposure_suppressesFlashUntilCompensationReturnsToZero() {
        runTest {
            val session = boundSession(PHOTO_SETTINGS)
            val imageCapture = mockk<ImageCapture>(relaxed = true)
            session.imageCapture = imageCapture
            runCurrent()
            session.setFlashMode(FlashMode.ON)
            verify { imageCapture.flashMode = ImageCapture.FLASH_MODE_ON }
            clearMocks(imageCapture)

            exposure.value = exposure.value?.copy(compensationIndex = 3)
            runCurrent()

            assertEquals(true, session.boundCamera.value?.isExposureCompensated)
            verify(exactly = 1) { imageCapture.flashMode = ImageCapture.FLASH_MODE_OFF }

            exposure.value = exposure.value?.copy(compensationIndex = -3)
            runCurrent()
            exposure.value = exposure.value?.copy(compensationIndex = 0)
            runCurrent()

            assertEquals(false, session.boundCamera.value?.isExposureCompensated)
            verifySequence {
                imageCapture.flashMode = ImageCapture.FLASH_MODE_OFF
                imageCapture.flashMode = ImageCapture.FLASH_MODE_ON
            }
        }
    }

    @Test
    fun lowLightBoost_staysSelectedDuringCompensationUntilTurnedOff() {
        runTest {
            every { cameraInfo.isLowLightBoostSupported } returns true
            val session = boundSession(PHOTO_SETTINGS)
            runCurrent()
            clearMocks(sceneLightControl, answers = false)

            session.setFlashMode(FlashMode.LOW_LIGHT_BOOST)
            exposure.value = exposure.value?.copy(compensationIndex = 3)
            runCurrent()

            verify(exactly = 0) { sceneLightControl.setFlashMode(FlashMode.OFF) }

            session.setFlashMode(FlashMode.OFF)

            verify(exactly = 1) { sceneLightControl.setFlashMode(FlashMode.OFF) }
        }
    }

    @Test
    fun videoLight_staysSelectedDuringCompensation() {
        runTest {
            val session = boundSession(VIDEO_SETTINGS)
            runCurrent()
            clearMocks(sceneLightControl, answers = false)

            session.setFlashMode(FlashMode.ON)
            exposure.value = exposure.value?.copy(compensationIndex = 3)
            runCurrent()

            verify(atLeast = 1) { sceneLightControl.setFlashMode(FlashMode.ON) }
            verify(exactly = 0) { sceneLightControl.setFlashMode(FlashMode.OFF) }
        }
    }

    @Test
    fun bind_attachesTheSceneLightToTheBoundCamera() {
        runTest {
            val session = boundSession(VIDEO_SETTINGS)

            session.setFlashMode(FlashMode.ON)

            verifyOrder {
                sceneLightControl.attach(
                    control = cameraControl,
                    boundCamera = match { boundCamera -> boundCamera.isVideoMode },
                )
                sceneLightControl.setFlashMode(FlashMode.ON)
            }
        }
    }

    @Test
    fun unbind_detachesTheSceneLight() {
        runTest {
            val session = boundSession(VIDEO_SETTINGS)

            session.unbind()

            verifyOrder {
                sceneLightControl.attach(control = cameraControl, boundCamera = any())
                sceneLightControl.detach()
            }
        }
    }

    @Test
    fun rebind_detachesTheSceneLightBeforeAttachingItAgain() {
        runTest {
            val session = boundSession(VIDEO_SETTINGS)

            session.bind(VIDEO_SETTINGS)

            verifyOrder {
                sceneLightControl.attach(control = cameraControl, boundCamera = any())
                sceneLightControl.detach()
                sceneLightControl.attach(control = cameraControl, boundCamera = any())
            }
        }
    }

    private fun TestScope.boundSession(settings: CameraBindSettings): CameraSessionImpl {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = CameraSessionImpl(
            cameraProviderSource = cameraProviderSource,
            extensionAvailabilityRepository = mockk(relaxed = true),
            extensionUsability = mockk(relaxed = true),
            cameraFeatureSupport = cameraFeatureSupport,
            featureGroupRequestResolver = featureGroupRequestResolver,
            snapshotSupportProbe = mockk(relaxed = true),
            snapshotProbeCache = mockk(relaxed = true),
            cameraSessionPlanFactory = cameraSessionPlanFactory,
            photoResolutionSupport = mockk(relaxed = true),
            videoBitrateSupport = mockk(relaxed = true),
            lowLightBoostSupport = mockk(relaxed = true),
            videoQualitySupport = mockk(relaxed = true),
            cameraXConstantsMapper = CameraXConstantsMapperImpl(),
            flashMapper = CameraFlashMapperImpl(),
            zoomControl = zoomControl,
            focusControl = focusControl,
            exposureControl = exposureControl,
            sceneLightControl = sceneLightControl,
            mainDispatcher = dispatcher,
            qrAnalysisDispatcher = dispatcher,
            scope = backgroundScope,
        )

        lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
        session.setPreviewTarget(
            PreviewTarget(
                lifecycleOwner = lifecycleOwner,
                surfaceProvider = mockk(relaxed = true),
                meteringPointFactory = mockk(relaxed = true),
            ),
        )
        session.initialize(forced = false, extensionMode = null)
        session.bind(settings)

        return session
    }

    private class TestLifecycleOwner : LifecycleOwner {

        val registry = LifecycleRegistry.createUnsafe(this)

        override val lifecycle: Lifecycle
            get() {
                return registry
            }
    }

    private companion object {
        private val QR_SETTINGS = CameraBindSettings(
            mode = CameraMode.QR_SCAN,
            aspectRatio = AspectRatio.RATIO_4_3,
            photo = null,
            video = null,
            qr = QrBindSettings(
                lensFacing = LensFacing.BACK,
                barcodeFormats = emptySet(),
            ),
        )

        private val PHOTO_SETTINGS = CameraBindSettings(
            mode = CameraMode.CAMERA,
            aspectRatio = AspectRatio.RATIO_4_3,
            photo = null,
            video = null,
            qr = null,
        )

        private val VIDEO_SETTINGS = PHOTO_SETTINGS.copy(
            mode = CameraMode.VIDEO,
            video = VideoBindSettings(
                quality = VideoQuality.FHD,
                frameRate = VideoFrameRate.FPS_30,
                stabilization = false,
                hdr = false,
                mirrorOnFrontCamera = false,
            ),
        )
    }
}
