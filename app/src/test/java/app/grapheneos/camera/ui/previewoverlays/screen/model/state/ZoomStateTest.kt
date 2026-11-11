package app.grapheneos.camera.ui.previewoverlays.screen.model.state

import app.grapheneos.camera.data.camera.model.CameraZoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoomStateTest {

    @Test
    fun perFrameValues_areTakenFromTheOtherState() {
        val state = ZoomState(
            value = zoom(ratio = 1f),
            isExpanded = true,
            interactionId = 1,
        )
        val other = ZoomState(
            value = zoom(
                ratio = 2f,
                linearZoom = 0.5f,
            ),
            interactionId = 4,
        )

        val expected = state.copy(
            value = other.value,
            interactionId = 4,
        )
        assertEquals(expected, state.withPerFrameValuesOf(other))
    }

    @Test
    fun theZoomRange_isKept() {
        val state = ZoomState(value = zoom(ratio = 1f))
        val other = ZoomState(
            value = zoom(
                ratio = 2f,
                maxZoomRatio = 4f,
            ),
        )

        val expected = ZoomState(value = zoom(ratio = 2f))
        assertEquals(expected, state.withPerFrameValuesOf(other))
    }

    @Test
    fun aZoomThatOnlyOneStateHas_isLeftAsItIs() {
        val loaded = ZoomState(value = zoom(ratio = 2f))
        val unloaded = ZoomState()

        assertNull(unloaded.withPerFrameValuesOf(loaded).value)
        assertEquals(loaded.value, loaded.withPerFrameValuesOf(unloaded).value)
    }

    private fun zoom(
        ratio: Float,
        linearZoom: Float = 0f,
        maxZoomRatio: Float = 8f,
    ): CameraZoom {
        return CameraZoom(
            zoomRatio = ratio,
            linearZoom = linearZoom,
            minZoomRatio = 0.5f,
            maxZoomRatio = maxZoomRatio,
        )
    }
}
