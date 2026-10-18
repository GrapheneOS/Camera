package app.grapheneos.camera.ui.gallery.components.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomGestureStateTest {

    private val state = ZoomGestureState()

    @Test
    fun initialState_isUnzoomedAndUntranslated() {
        assertEquals(1f, state.scale)
        assertEquals(Offset.Zero, state.offset)
        assertFalse(state.isZoomed())
        assertFalse(state.isZoomAnimationEnabled)
    }

    @Test
    fun applyTransform_clampsTheScaleToTheSupportedRange() {
        state.updateLayout(containerSize = SQUARE, contentSize = SQUARE)

        zoomAroundTheCenter(containerSize = SQUARE, zoomChange = 10f)
        assertEquals(3f, state.scale)
        zoomAroundTheCenter(
            containerSize = SQUARE,
            zoomChange = 0.1f,
            panChange = Offset(x = 50f, y = 50f),
        )
        assertEquals(1f, state.scale)
        assertEquals(Offset.Zero, state.offset)
        assertFalse(state.isZoomed())
    }

    @Test
    fun applyTransform_keepsThePanWithinTheScaledContent() {
        val tall = Size(width = 100f, height = 200f)
        state.updateLayout(containerSize = tall, contentSize = tall)

        zoomAroundTheCenter(containerSize = tall, zoomChange = 3f)
        zoomAroundTheCenter(
            containerSize = tall,
            zoomChange = 1f,
            panChange = Offset(x = 250f, y = -250f),
        )
        assertEquals(Offset(x = 100f, y = -200f), state.offset)
    }

    @Test
    fun applyTransform_ofALetterboxedPhoto_doesNotPanIntoTheBars() {
        state.updateLayout(
            containerSize = LETTERBOX_CONTAINER,
            contentSize = Size(width = 100f, height = 50f),
        )

        zoomAroundTheCenter(containerSize = LETTERBOX_CONTAINER, zoomChange = 2f)
        zoomAroundTheCenter(
            containerSize = LETTERBOX_CONTAINER,
            zoomChange = 1f,
            panChange = Offset(x = 80f, y = 80f),
        )

        assertEquals(Offset(x = 50f, y = 0f), state.offset)
    }

    @Test
    fun updateLayout_reclampsTheExistingOffset() {
        val large = Size(width = 200f, height = 200f)
        state.updateLayout(containerSize = large, contentSize = large)

        zoomAroundTheCenter(containerSize = large, zoomChange = 3f)
        zoomAroundTheCenter(
            containerSize = large,
            zoomChange = 1f,
            panChange = Offset(x = 300f, y = 300f),
        )

        state.updateLayout(containerSize = SQUARE, contentSize = SQUARE)

        assertEquals(Offset(x = 100f, y = 100f), state.offset)
    }

    @Test
    fun applyTransform_keepsThePinchCentroidInPlace() {
        state.updateLayout(containerSize = SQUARE, contentSize = SQUARE)
        state.applyTransform(
            centroid = Offset(x = 75f, y = 75f),
            zoomChange = 2f,
            panChange = Offset.Zero,
        )

        assertEquals(Offset(x = -25f, y = -25f), state.offset)
    }

    @Test
    fun applyTransform_whenTheLastFingerLifts_keepsTheZoom() {
        state.updateLayout(containerSize = SQUARE, contentSize = SQUARE)

        zoomAroundTheCenter(containerSize = SQUARE, zoomChange = 2f)

        state.applyTransform(
            centroid = Offset.Unspecified,
            zoomChange = 1f,
            panChange = Offset.Zero,
        )

        assertEquals(2f, state.scale)
        assertEquals(Offset.Zero, state.offset)
    }

    @Test
    fun applyDoubleTap_whenUnzoomed_zoomsInAroundTheTap() {
        state.updateLayout(containerSize = SQUARE, contentSize = SQUARE)
        state.applyDoubleTap(tapOffset = Offset(x = 75f, y = 25f))

        assertEquals(2.5f, state.scale)
        assertEquals(Offset(x = -37.5f, y = 37.5f), state.offset)
        assertTrue(state.isZoomed())
        assertTrue(state.isZoomAnimationEnabled)
    }

    @Test
    fun applyDoubleTap_whenZoomed_zoomsBackOut() {
        state.updateLayout(containerSize = SQUARE, contentSize = SQUARE)
        state.applyDoubleTap(tapOffset = Offset(x = 75f, y = 25f))
        state.applyDoubleTap(tapOffset = Offset(x = 50f, y = 50f))

        assertEquals(1f, state.scale)
        assertEquals(Offset.Zero, state.offset)
        assertFalse(state.isZoomed())
    }

    private fun zoomAroundTheCenter(
        containerSize: Size,
        zoomChange: Float,
        panChange: Offset = Offset.Zero,
    ) {
        state.applyTransform(
            centroid = containerSize.center,
            zoomChange = zoomChange,
            panChange = panChange,
        )
    }

    private companion object {
        val SQUARE = Size(width = 100f, height = 100f)
        val LETTERBOX_CONTAINER = Size(width = 100f, height = 200f)
    }
}
