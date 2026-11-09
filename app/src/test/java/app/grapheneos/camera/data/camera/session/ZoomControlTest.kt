package app.grapheneos.camera.data.camera.session

import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.ZoomState
import androidx.lifecycle.MutableLiveData
import app.grapheneos.camera.data.camera.mapper.CameraXStateMapperImpl
import app.grapheneos.camera.data.camera.model.CameraZoomEvent
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
    private val cameraControl = mockk<CameraControl>(relaxed = true)
    private val cameraInfo = mockk<CameraInfo> {
        every { zoomState } returns zoomStates
    }
    private val camera = mockk<Camera> {
        every { cameraControl } returns this@ZoomControlTest.cameraControl
        every { cameraInfo } returns this@ZoomControlTest.cameraInfo
    }
    private val events = mutableListOf<CameraZoomEvent>()

    @Test
    fun attach_readsTheZoomStateOnALaterMessage() {
        runTest {
            val zoomControl = createZoomControl()

            zoomControl.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                remembersZoom = true,
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
                remembersZoom = true,
            )
            zoomControl.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                remembersZoom = true,
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
                remembersZoom = true,
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
                remembersZoom = true,
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
    fun detach_stopsObservingAndForgetsTheState() {
        runTest {
            val zoomControl = createZoomControl()
            zoomControl.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                remembersZoom = true,
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
                remembersZoom = true,
            )
            runCurrent()

            zoomControl.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                remembersZoom = true,
            )
            runCurrent()

            assertEquals(listOf(CameraZoomEvent.Loaded, CameraZoomEvent.Loaded), events)
        }
    }

    @Test
    fun zoomRequests_reachTheAttachedCameraOnly() {
        runTest {
            val zoomControl = createZoomControl()
            zoomControl.setZoomRatio(2f)

            zoomControl.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                remembersZoom = true,
            )
            zoomControl.setZoomRatio(3f)

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
                remembersZoom = true,
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
                remembersZoom = true,
            )
            runCurrent()
            val animation = launch(mainDispatcherRule.testDispatcher) {
                control.animateZoomRatio(4f)
            }
            advanceTimeBy(80.milliseconds)

            control.setZoomRatio(1.5f)
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
                remembersZoom = true,
            )
            runCurrent()
            val animation = launch(mainDispatcherRule.testDispatcher) {
                control.animateZoomRatio(4f)
            }
            advanceTimeBy(250.milliseconds)
            assertFalse(animation.isCompleted)

            control.setZoomRatio(1.5f)
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
                remembersZoom = true,
            )
            runCurrent()
            val animation = launch(mainDispatcherRule.testDispatcher) {
                control.animateZoomRatio(4f)
            }
            advanceTimeBy(80.milliseconds)

            control.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                remembersZoom = true,
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
                remembersZoom = true,
            )
            runCurrent()

            verify(exactly = 1) { cameraControl.setZoomRatio(4f) }
            assertEquals(4f, control.zoom?.zoomRatio)
            assertEquals(listOf(CameraZoomEvent.Loaded), events)
        }
    }

    @Test
    fun eachLens_keepsItsOwnRatio() {
        runTest {
            val control = createZoomControl()
            control.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                remembersZoom = true,
            )
            runCurrent()
            control.setZoomRatio(2f)

            control.attach(
                camera = camera,
                lensFacing = LensFacing.FRONT,
                remembersZoom = true,
            )
            runCurrent()

            assertEquals(1f, control.zoom?.zoomRatio)
            control.setZoomRatio(3f)

            control.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                remembersZoom = true,
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
                remembersZoom = true,
            )
            runCurrent()
            control.setZoomRatio(2f)

            control.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                remembersZoom = false,
            )
            runCurrent()

            assertEquals(1f, control.zoom?.zoomRatio)
            control.setZoomRatio(3f)

            control.attach(
                camera = camera,
                lensFacing = LensFacing.BACK,
                remembersZoom = true,
            )
            runCurrent()

            assertEquals(2f, control.zoom?.zoomRatio)
            verify(exactly = 2) { cameraControl.setZoomRatio(2f) }
        }
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

    private fun zoomState(ratio: Float): ZoomState {
        return mockk {
            every { zoomRatio } returns ratio
            every { linearZoom } returns 0f
            every { minZoomRatio } returns 1f
            every { maxZoomRatio } returns 4f
        }
    }
}
