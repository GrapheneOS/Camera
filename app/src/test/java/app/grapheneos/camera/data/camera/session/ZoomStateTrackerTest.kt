package app.grapheneos.camera.data.camera.session

import androidx.camera.core.ZoomState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import app.grapheneos.camera.data.camera.model.CameraZoomEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ZoomStateTrackerTest {

    private val lifecycleOwner = object : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
    }

    private val events = mutableListOf<CameraZoomEvent>()

    private val tracker = ZoomStateTrackerImpl()

    @Test
    fun attach_reportsTheFirstStateAsLoadedAndLaterRatiosAsChanged() {
        val source = MutableLiveData(zoomState(ratio = 1f))

        tracker.attach(source = source, lifecycleOwner = lifecycleOwner, onEvent = events::add)
        source.value = zoomState(ratio = 1f)
        source.value = zoomState(ratio = 2f)

        assertEquals(
            listOf(CameraZoomEvent.Loaded, CameraZoomEvent.Changed),
            events,
        )
        assertEquals(2f, tracker.zoomState?.zoomRatio)
    }

    @Test
    fun detach_stopsObservingAndForgetsTheState() {
        val source = MutableLiveData(zoomState(ratio = 1f))
        tracker.attach(source = source, lifecycleOwner = lifecycleOwner, onEvent = events::add)

        tracker.detach()
        source.value = zoomState(ratio = 2f)

        assertFalse(source.hasObservers())
        assertNull(tracker.zoomState)
        assertEquals(listOf(CameraZoomEvent.Loaded), events)
    }

    @Test
    fun attach_afterDetach_reportsTheNewSourceAsLoaded() {
        tracker.attach(
            source = MutableLiveData(zoomState(ratio = 1f)),
            lifecycleOwner = lifecycleOwner,
            onEvent = events::add,
        )
        tracker.detach()

        tracker.attach(
            source = MutableLiveData(zoomState(ratio = 1f)),
            lifecycleOwner = lifecycleOwner,
            onEvent = events::add,
        )

        assertEquals(
            listOf(CameraZoomEvent.Loaded, CameraZoomEvent.Loaded),
            events,
        )
    }

    private fun zoomState(ratio: Float): ZoomState {
        return mockk {
            every { zoomRatio } returns ratio
            every { linearZoom } returns 0f
        }
    }
}
