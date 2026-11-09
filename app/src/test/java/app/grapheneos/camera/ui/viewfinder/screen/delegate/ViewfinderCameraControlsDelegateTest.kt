package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.camera.model.CameraZoomEvent
import app.grapheneos.camera.data.camera.session.ExposureControl
import app.grapheneos.camera.data.camera.session.ZoomControl
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.testutil.viewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.mapper.NextZoomStopMapperImpl
import app.grapheneos.camera.ui.viewfinder.screen.model.input.ZoomDirection
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ViewfinderCameraControlsDelegateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val scope = TestScope(mainDispatcherRule.testDispatcher)

    private val zoomControl = mockk<ZoomControl>(relaxed = true)
    private val exposureControl = mockk<ExposureControl>(relaxed = true)
    private val zoomEvents = MutableSharedFlow<CameraZoomEvent>(extraBufferCapacity = 8)

    private val stateHolder = viewfinderStateHolder(mode = CameraMode.CAMERA)

    @Before
    fun setUp() {
        every { zoomControl.events } returns zoomEvents
    }

    @After
    fun cancelScope() {
        scope.cancel()
    }

    @Test
    fun zoomKeys_inQrMode_areLeftAlone() {
        every { zoomControl.zoom } returns ZOOM
        val delegate = createDelegate()
        stateHolder.update { it.copy(mode = CameraMode.QR_SCAN) }

        delegate.stepZoom(step = 1f)

        verify(exactly = 0) { zoomControl.setZoomRatio(any()) }
    }

    @Test
    fun stepZoom_pastEitherEnd_stopsThere() {
        every { zoomControl.zoom } returns ZOOM

        val delegate = createDelegate()
        delegate.stepZoom(step = 100f)
        delegate.stepZoom(step = -100f)

        verifyOrder {
            zoomControl.setZoomRatio(ZOOM.maxZoomRatio)
            zoomControl.setZoomRatio(ZOOM.minZoomRatio)
        }
    }

    @Test
    fun stepZoom_outOfTheWideAngleCamera_stopsAtThePrimaryOne() {
        every { zoomControl.zoom } returns ZOOM.copy(zoomRatio = 0.6f, minZoomRatio = 0.5f)

        val delegate = createDelegate()
        delegate.stepZoom(step = 1f)

        verify(exactly = 1) { zoomControl.setZoomRatio(1f) }
    }

    @Test
    fun stepZoom_beforeTheZoomIsKnown_leavesTheZoomAlone() {
        every { zoomControl.zoom } returns null

        val delegate = createDelegate()
        delegate.stepZoom(step = 1f)

        verify(exactly = 0) { zoomControl.setZoomRatio(any()) }
    }

    @Test
    fun zoomStepping_stepsAtOnceThenAtEachIntervalWhileHeld() {
        trackZoomRatio()

        val delegate = createDelegate()
        delegate.startZoomStepping(ZoomDirection.IN)
        verify(exactly = 1) { zoomControl.setZoomRatio(2f) }

        scope.advanceTimeBy(ZOOM_STOP_INTERVAL)
        scope.runCurrent()

        verify(exactly = 1) { zoomControl.setZoomRatio(ZOOM.maxZoomRatio) }
    }

    @Test
    fun zoomStepping_stopped_takesNoFurtherStep() {
        trackZoomRatio()

        val delegate = createDelegate()
        delegate.startZoomStepping(ZoomDirection.IN)
        delegate.stopZoomStepping()
        scope.advanceTimeBy(ZOOM_STOP_INTERVAL * 4)
        scope.runCurrent()

        verify(exactly = 1) { zoomControl.setZoomRatio(any()) }
    }

    @Test
    fun scaleZoom_scalesTheCurrentRatio() {
        every { zoomControl.zoom } returns ZOOM

        val delegate = createDelegate()
        delegate.scaleZoom(scaleFactor = 1.5f)

        verify(exactly = 1) { zoomControl.setZoomRatio(ZOOM.zoomRatio * 1.5f) }
    }

    @Test
    fun setExposureCompensation_isAppliedAndPublished() {
        stateHolder.update { it.copy(session = it.session.copy(exposure = EXPOSURE)) }

        val delegate = createDelegate()
        delegate.setExposureCompensation(compensationIndex = 5)

        verify(exactly = 1) { exposureControl.setCompensationIndex(5) }
        assertEquals(5, stateHolder.state.value.session.exposure?.compensationIndex)
    }

    @Test
    fun zoomStates_publishTheZoomAndOnlyAChangeIsHandedOn() {
        every { zoomControl.zoom } returns ZOOM
        val delegate = createDelegate()
        var changes = 0
        scope.launch {
            delegate.zoomChanges.collect { changes++ }
        }

        zoomEvents.tryEmit(CameraZoomEvent.Loaded)
        assertEquals(ZOOM, stateHolder.state.value.session.zoom)
        assertEquals(0, changes)

        zoomEvents.tryEmit(CameraZoomEvent.Changed)
        assertEquals(1, changes)
    }

    private fun trackZoomRatio() {
        var zoomRatio = 1f
        every { zoomControl.zoom } answers { ZOOM.copy(zoomRatio = zoomRatio, minZoomRatio = 0.5f) }
        every { zoomControl.setZoomRatio(any()) } answers { zoomRatio = firstArg() }
    }

    private fun createDelegate(): ViewfinderCameraControlsDelegate {
        val delegate = ViewfinderCameraControlsDelegateImpl(
            zoomControl = zoomControl,
            exposureControl = exposureControl,
            nextZoomStopMapper = NextZoomStopMapperImpl(),
        )

        delegate.bind(
            scope = scope,
            stateHolder = stateHolder,
        )
        scope.launch {
            delegate.trackZoom()
        }

        return delegate
    }

    private companion object {
        val ZOOM_STOP_INTERVAL = 500.milliseconds
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
    }
}
