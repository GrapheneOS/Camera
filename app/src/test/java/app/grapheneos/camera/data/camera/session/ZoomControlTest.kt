package app.grapheneos.camera.data.camera.session

import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.ZoomState
import androidx.lifecycle.MutableLiveData
import app.grapheneos.camera.data.camera.mapper.CameraXStateMapperImpl
import app.grapheneos.camera.data.camera.model.CameraZoomEvent
import app.grapheneos.camera.data.camera.session.ZoomControl.Restoration
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ZoomControlTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zoomStates = MutableLiveData(zoomState(ratio = 1f))
    private val cameraControl = mockk<CameraControl>(relaxed = true) {
        every { setZoomRatio(any()) } answers {
            val ratio = firstArg<Float>()
            if (ratio in MIN_ZOOM_RATIO..MAX_ZOOM_RATIO) {
                zoomStates.value = zoomState(ratio = ratio)
            }
            mockk(relaxed = true)
        }
    }
    private val cameraInfo = mockk<CameraInfo> {
        every { zoomState } returns zoomStates
    }
    private val camera = mockk<Camera> {
        every { cameraControl } returns this@ZoomControlTest.cameraControl
        every { cameraInfo } returns this@ZoomControlTest.cameraInfo
    }
    private val events = mutableListOf<CameraZoomEvent>()

    @Test
    fun attach_withoutASavedRatio_readsTheZoomStateOnALaterMessage() {
        runTest {
            val zoomControl = createZoomControl()

            zoomControl.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.BEFORE_PREVIEW,
            )

            verify(exactly = 0) { cameraInfo.zoomState }
            assertNull(zoomControl.zoom)
            assertEquals(LensFacing.BACK, zoomControl.lensFacing)

            runCurrent()

            verify(exactly = 1) { cameraInfo.zoomState }
            assertEquals(listOf(CameraZoomEvent.Loaded), events)
            assertEquals(1f, zoomControl.zoom?.zoomRatio)
        }
    }

    @Test
    fun attachTwiceBeforeItRuns_readsTheZoomStateOnce() {
        runTest {
            val zoomControl = createZoomControl()

            zoomControl.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            zoomControl.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            runCurrent()

            verify(exactly = 1) { cameraInfo.zoomState }
        }
    }

    @Test
    fun detachBeforeItRuns_neverReadsTheZoomState() {
        runTest {
            val zoomControl = createZoomControl()

            zoomControl.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            zoomControl.detach()
            runCurrent()

            verify(exactly = 0) { cameraInfo.zoomState }
        }
    }

    @Test
    fun laterRatios_areReportedAsChanged() {
        runTest {
            val zoomControl = createZoomControl()
            zoomControl.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            runCurrent()

            zoomStates.value = zoomState(ratio = 1f)
            runCurrent()
            zoomStates.value = zoomState(ratio = 2f)
            runCurrent()

            assertEquals(listOf(CameraZoomEvent.Loaded, CameraZoomEvent.Changed), events)
            assertEquals(2f, zoomControl.zoom?.zoomRatio)
        }
    }

    @Test
    fun aRangeChange_reportsTheNewLimitsAtTheSameRatio() {
        runTest {
            val control = createZoomControl()
            control.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            runCurrent()

            zoomStates.value = zoomState(
                ratio = 1f,
                maximum = 2f,
            )
            runCurrent()

            assertEquals(listOf(CameraZoomEvent.Loaded, CameraZoomEvent.Changed), events)
            assertEquals(2f, control.zoom?.maxZoomRatio)
        }
    }

    @Test
    fun detach_stopsObservingAndForgetsTheState() {
        runTest {
            val zoomControl = createZoomControl()
            zoomControl.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            runCurrent()

            zoomControl.detach()
            runCurrent()
            zoomStates.value = zoomState(ratio = 2f)

            assertFalse(zoomStates.hasObservers())
            assertNull(zoomControl.zoom)
            assertNull(zoomControl.lensFacing)
            assertEquals(listOf(CameraZoomEvent.Loaded), events)
        }
    }

    @Test
    fun reattach_reportsTheNewStateAsLoaded() {
        runTest {
            val zoomControl = createZoomControl()
            zoomControl.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            runCurrent()

            zoomControl.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            runCurrent()

            assertEquals(listOf(CameraZoomEvent.Loaded, CameraZoomEvent.Loaded), events)
        }
    }

    @Test
    fun zoomRequests_reachTheCameraOfTheirLensOnly() {
        runTest {
            val zoomControl = createZoomControl()
            zoomControl.setZoomRatio(
                zoomRatio = 2f,
                lensFacing = LensFacing.FRONT,
            )

            zoomControl.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            zoomControl.setZoomRatio(
                zoomRatio = 3f,
                lensFacing = LensFacing.BACK,
            )
            runCurrent()

            verify(exactly = 0) { cameraControl.setZoomRatio(2f) }
            verify(exactly = 1) { cameraControl.setZoomRatio(3f) }
        }
    }

    @Test
    fun animatedZoom_changesTheCameraGraduallyAndEndsAtTheClampedTarget() {
        runTest {
            val control = createZoomControl()
            val ratios = mutableListOf<Float>()
            every { cameraControl.setZoomRatio(capture(ratios)) } answers {
                zoomStates.value = zoomState(ratio = firstArg())
                mockk(relaxed = true)
            }
            control.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            runCurrent()

            val animation = launch(mainDispatcherRule.testDispatcher) {
                control.animateZoomRatio(100f)
            }
            advanceTimeBy(100.milliseconds)
            assertTrue(ratios.size > 1)
            assertTrue(ratios.all { it > 1f && it < 4f })

            advanceTimeBy(100.milliseconds)
            runCurrent()

            assertTrue(animation.isCompleted)
            assertEquals(4f, ratios.last())
        }
    }

    @Test
    fun aDirectZoomRequest_cancelsTheAnimationBeforeApplyingItsValue() {
        runTest {
            val control = createZoomControl()
            val ratios = mutableListOf<Float>()
            every { cameraControl.setZoomRatio(capture(ratios)) } returns mockk(relaxed = true)
            control.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            runCurrent()
            val animation = launch(mainDispatcherRule.testDispatcher) {
                control.animateZoomRatio(4f)
            }
            advanceTimeBy(80.milliseconds)

            control.setZoomRatio(
                zoomRatio = 1.5f,
                lensFacing = LensFacing.BACK,
            )
            val afterManualInput = ratios.toList()
            advanceTimeBy(300.milliseconds)

            assertTrue(animation.isCompleted)
            assertFalse(animation.isCancelled)
            assertEquals(1.5f, ratios.last())
            assertEquals(afterManualInput, ratios)
        }
    }

    @Test
    fun aRequestAwaitingCameraAcknowledgement_isStillCancelledByManualInput() {
        runTest {
            val control = createZoomControl()
            val ratios = mutableListOf<Float>()
            every { cameraControl.setZoomRatio(capture(ratios)) } returns mockk(relaxed = true)
            control.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            runCurrent()
            val animation = launch(mainDispatcherRule.testDispatcher) {
                control.animateZoomRatio(4f)
            }
            advanceTimeBy(250.milliseconds)
            assertFalse(animation.isCompleted)

            control.setZoomRatio(
                zoomRatio = 1.5f,
                lensFacing = LensFacing.BACK,
            )
            advanceTimeBy(300.milliseconds)

            assertTrue(animation.isCompleted)
            assertFalse(animation.isCancelled)
            assertEquals(1.5f, ratios.last())
        }
    }

    @Test
    fun replacingTheCamera_stopsAllCommandsToThePreviousCamera() {
        runTest {
            val control = createZoomControl()
            val ratios = mutableListOf<Float>()
            every { cameraControl.setZoomRatio(capture(ratios)) } returns mockk(relaxed = true)
            control.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            runCurrent()
            val animation = launch(mainDispatcherRule.testDispatcher) {
                control.animateZoomRatio(4f)
            }
            advanceTimeBy(80.milliseconds)

            control.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            val beforeRebind = ratios.toList()
            advanceTimeBy(300.milliseconds)

            assertTrue(animation.isCompleted)
            assertFalse(animation.isCancelled)
            assertEquals(beforeRebind + 4f, ratios)
        }
    }

    @Test
    fun aRememberedRatio_isAppliedWithinTheNextCamerasRange() {
        runTest {
            val control = createZoomControl()
            control.rememberZoomRatio(
                zoomRatio = 8f,
                lensFacing = LensFacing.FRONT,
            )

            control.attach(
                camera = camera,
                lensFacing = LensFacing.FRONT,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            runCurrent()

            verify(exactly = 1) { cameraControl.setZoomRatio(4f) }
            assertEquals(4f, control.zoom?.zoomRatio)
            assertEquals(listOf(CameraZoomEvent.Loaded), events)
        }
    }

    @Test
    fun anAppliedRememberedRatio_reachesTheCameraBeforeItsZoomStateIsRead() {
        runTest {
            val control = createZoomControl()
            control.rememberZoomRatio(
                zoomRatio = 2f,
                lensFacing = LensFacing.BACK,
            )

            control.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.BEFORE_PREVIEW,
            )

            verify(exactly = 1) { cameraControl.setZoomRatio(2f) }
            assertNull(control.zoom)

            runCurrent()

            verify(exactly = 1) { cameraControl.setZoomRatio(2f) }
            assertEquals(2f, control.zoom?.zoomRatio)
            assertEquals(listOf(CameraZoomEvent.Loaded), events)
        }
    }

    @Test
    fun anAppliedRememberedRatio_staysWithinTheCamerasRange() {
        runTest {
            val control = createZoomControl()
            control.rememberZoomRatio(
                zoomRatio = 8f,
                lensFacing = LensFacing.BACK,
            )

            control.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.BEFORE_PREVIEW,
            )
            runCurrent()

            verify(exactly = 1) { cameraControl.setZoomRatio(4f) }
            assertEquals(4f, control.zoom?.zoomRatio)
        }
    }

    @Test
    fun aRatioRequestedForADetachedLens_reachesItsCameraOnAttach() {
        runTest {
            val control = createZoomControl()
            control.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            control.setZoomRatio(
                zoomRatio = 3f,
                lensFacing = LensFacing.FRONT,
            )
            verify(exactly = 0) { cameraControl.setZoomRatio(any()) }

            control.rebind(
                camera = camera,
                lensFacing = LensFacing.FRONT,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            runCurrent()

            verify(exactly = 1) { cameraControl.setZoomRatio(3f) }
        }
    }

    @Test
    fun aRememberedRatioThatIsNotApplied_waitsForTheZoomState() {
        runTest {
            val control = createZoomControl()
            control.rememberZoomRatio(
                zoomRatio = 2f,
                lensFacing = LensFacing.BACK,
            )

            control.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )

            verify(exactly = 0) { cameraControl.setZoomRatio(any()) }

            runCurrent()

            verify(exactly = 1) { cameraControl.setZoomRatio(2f) }
            assertEquals(2f, control.zoom?.zoomRatio)
        }
    }

    @Test
    fun aCameraAttachedWithoutMemory_isNotZoomedOnAttach() {
        runTest {
            val control = createZoomControl()
            control.rememberZoomRatio(
                zoomRatio = 2f,
                lensFacing = LensFacing.BACK,
            )

            control.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.DISABLED,
            )
            verify(exactly = 0) { cameraInfo.zoomState }
            runCurrent()

            verify(exactly = 0) { cameraControl.setZoomRatio(any()) }
            assertEquals(1f, control.zoom?.zoomRatio)
        }
    }

    @Test
    fun eachLens_keepsItsOwnRatio() {
        runTest {
            val control = createZoomControl()
            control.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            runCurrent()
            control.setZoomRatio(
                zoomRatio = 2f,
                lensFacing = LensFacing.BACK,
            )

            control.rebind(
                camera = camera,
                lensFacing = LensFacing.FRONT,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            runCurrent()

            assertEquals(1f, control.zoom?.zoomRatio)
            control.setZoomRatio(
                zoomRatio = 3f,
                lensFacing = LensFacing.FRONT,
            )

            control.rebind(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            runCurrent()

            assertEquals(2f, control.zoom?.zoomRatio)
            verify(exactly = 2) { cameraControl.setZoomRatio(2f) }
            verify(exactly = 1) { cameraControl.setZoomRatio(3f) }
        }
    }

    @Test
    fun aCameraAttachedWithoutMemory_neitherGetsNorReplacesTheRememberedRatio() {
        runTest {
            val control = createZoomControl()
            control.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            runCurrent()
            control.setZoomRatio(
                zoomRatio = 2f,
                lensFacing = LensFacing.BACK,
            )

            control.rebind(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.DISABLED,
            )
            control.setZoomRatio(
                zoomRatio = 8f,
                lensFacing = LensFacing.BACK,
            )
            runCurrent()

            assertEquals(4f, control.zoom?.zoomRatio)

            control.rebind(
                camera = camera,
                lensFacing = LensFacing.BACK,
                restoration = Restoration.WHEN_ZOOM_LOADS,
            )
            runCurrent()

            assertEquals(2f, control.zoom?.zoomRatio)
            verify(exactly = 2) { cameraControl.setZoomRatio(2f) }
        }
    }

    // CameraX resets the zoom of a camera whose use cases are all unbound.
    private fun ZoomControl.rebind(
        camera: Camera,
        lensFacing: LensFacing,
        restoration: Restoration,
    ) {
        detach()
        zoomStates.value = zoomState(ratio = MIN_ZOOM_RATIO)
        attach(
            camera = camera,
            lensFacing = lensFacing,
            restoration = restoration,
        )
    }

    private fun TestScope.createZoomControl(): ZoomControl {
        val zoomControl = ZoomControlImpl(
            cameraXStateMapper = CameraXStateMapperImpl(),
            scope = backgroundScope,
            mainLooperDispatcher = StandardTestDispatcher(testScheduler),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            zoomControl.events.collect { event -> events += event }
        }
        return zoomControl
    }

    private fun zoomState(
        ratio: Float,
        maximum: Float = MAX_ZOOM_RATIO,
    ): ZoomState {
        return mockk {
            every { zoomRatio } returns ratio
            every { linearZoom } returns 0f
            every { minZoomRatio } returns MIN_ZOOM_RATIO
            every { maxZoomRatio } returns maximum
        }
    }

    private companion object {
        private const val MIN_ZOOM_RATIO = 1f
        private const val MAX_ZOOM_RATIO = 4f
    }
}
