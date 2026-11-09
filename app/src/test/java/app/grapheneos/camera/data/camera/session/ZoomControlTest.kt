package app.grapheneos.camera.data.camera.session

import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.ZoomState
import androidx.lifecycle.MutableLiveData
import app.grapheneos.camera.data.camera.mapper.CameraXStateMapperImpl
import app.grapheneos.camera.data.camera.model.CameraZoomEvent
import app.grapheneos.camera.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

            zoomControl.attach(camera)

            verify(exactly = 0) { cameraInfo.zoomState }
            assertNull(zoomControl.zoom)

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

            zoomControl.attach(camera)
            zoomControl.attach(camera)
            runCurrent()

            verify(exactly = 1) { cameraInfo.zoomState }
        }
    }

    @Test
    fun detachBeforeItRuns_neverReadsTheZoomState() {
        runTest {
            val zoomControl = createZoomControl()

            zoomControl.attach(camera)
            zoomControl.detach()
            runCurrent()

            verify(exactly = 0) { cameraInfo.zoomState }
        }
    }

    @Test
    fun laterRatios_areReportedAsChanged() {
        runTest {
            val zoomControl = createZoomControl()
            zoomControl.attach(camera)
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
            zoomControl.attach(camera)
            runCurrent()

            zoomControl.detach()
            runCurrent()
            zoomStates.value = zoomState(ratio = 2f)

            assertFalse(zoomStates.hasObservers())
            assertNull(zoomControl.zoom)
            assertEquals(listOf(CameraZoomEvent.Loaded), events)
        }
    }

    @Test
    fun reattach_reportsTheNewStateAsLoaded() {
        runTest {
            val zoomControl = createZoomControl()
            zoomControl.attach(camera)
            runCurrent()

            zoomControl.attach(camera)
            runCurrent()

            assertEquals(listOf(CameraZoomEvent.Loaded, CameraZoomEvent.Loaded), events)
        }
    }

    @Test
    fun zoomRequests_reachTheAttachedCameraOnly() {
        runTest {
            val zoomControl = createZoomControl()
            zoomControl.setZoomRatio(2f)

            zoomControl.attach(camera)
            zoomControl.setZoomRatio(3f)
            zoomControl.setLinearZoom(0.5f)

            verify(exactly = 0) { cameraControl.setZoomRatio(2f) }
            verify(exactly = 1) { cameraControl.setZoomRatio(3f) }
            verify(exactly = 1) { cameraControl.setLinearZoom(0.5f) }
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
