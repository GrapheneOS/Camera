package app.grapheneos.camera.data.camera.session

import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.SessionConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import app.grapheneos.camera.data.camera.mapper.CameraXConstantsMapperImpl
import app.grapheneos.camera.data.camera.mapper.CameraXStateMapperImpl
import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.camera.model.CameraSessionPlan
import app.grapheneos.camera.data.camera.model.FeatureGroupRequest
import app.grapheneos.camera.data.camera.model.LensFeatures
import app.grapheneos.camera.data.camera.model.PreviewTarget
import app.grapheneos.camera.data.camera.model.VideoBindSettings
import app.grapheneos.camera.data.camera.model.VideoFeatureGroup
import app.grapheneos.camera.data.camera.repository.CameraProviderSource
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verifySequence
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraSessionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val lifecycleOwner = TestLifecycleOwner()

    private val sceneLightControl = mockk<SceneLightControl>(relaxed = true)

    private val cameraControl = mockk<CameraControl>(relaxed = true)

    private val camera = mockk<Camera>(relaxed = true) {
        every { cameraControl } returns this@CameraSessionTest.cameraControl
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
    }

    @Test
    fun bind_attachesTheSceneLightToTheBoundCamera() {
        val session = boundSession()

        session.setFlashMode(FlashMode.ON)

        verifySequence {
            sceneLightControl.detach()
            sceneLightControl.attach(
                control = cameraControl,
                boundCamera = match { boundCamera -> boundCamera.isVideoMode },
            )
            sceneLightControl.setFlashMode(FlashMode.ON)
        }
    }

    @Test
    fun unbind_detachesTheSceneLight() {
        val session = boundSession()

        session.unbind()

        verifySequence {
            sceneLightControl.detach()
            sceneLightControl.attach(control = cameraControl, boundCamera = any())
            sceneLightControl.detach()
        }
    }

    @Test
    fun rebind_detachesTheSceneLightBeforeAttachingItAgain() {
        val session = boundSession()

        session.bind(VIDEO_SETTINGS)

        verifySequence {
            sceneLightControl.detach()
            sceneLightControl.attach(control = cameraControl, boundCamera = any())
            sceneLightControl.detach()
            sceneLightControl.attach(control = cameraControl, boundCamera = any())
        }
    }

    private fun boundSession(): CameraSessionImpl {
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
            videoQualitySupport = mockk(relaxed = true),
            cameraXConstantsMapper = CameraXConstantsMapperImpl(),
            cameraXStateMapper = CameraXStateMapperImpl(),
            sceneLightControl = sceneLightControl,
            mainDispatcher = mainDispatcherRule.testDispatcher,
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
        session.bind(VIDEO_SETTINGS)

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
        val VIDEO_SETTINGS = CameraBindSettings(
            mode = CameraMode.VIDEO,
            aspectRatio = AspectRatio.RATIO_4_3,
            photo = null,
            video = VideoBindSettings(
                quality = VideoQuality.FHD,
                frameRate = VideoFrameRate.FPS_30,
                stabilization = false,
                hdr = false,
                mirrorOnFrontCamera = false,
            ),
            qr = null,
        )
    }
}
