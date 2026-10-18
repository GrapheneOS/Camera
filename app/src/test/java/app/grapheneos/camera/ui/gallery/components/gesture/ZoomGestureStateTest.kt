package app.grapheneos.camera.ui.gallery.components.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

        assertTrue(state.applyTransform(zoomChange = 10f, panChange = Offset.Zero))
        assertEquals(3f, state.scale)
        assertTrue(state.applyTransform(zoomChange = 0.1f, panChange = Offset(x = 50f, y = 50f)))
        assertEquals(1f, state.scale)
        assertEquals(Offset.Zero, state.offset)
        assertFalse(state.isZoomed())
    }

    @Test
    fun applyTransform_keepsThePanWithinTheScaledContent() {
        val tall = Size(width = 100f, height = 200f)
        state.updateLayout(containerSize = tall, contentSize = tall)
        state.applyTransform(zoomChange = 3f, panChange = Offset.Zero)

        assertFalse(state.applyTransform(zoomChange = 1f, panChange = Offset(x = 250f, y = -250f)))
        assertEquals(Offset(x = 100f, y = -200f), state.offset)
    }

    @Test
    fun applyTransform_ofALetterboxedPhoto_doesNotPanIntoTheBars() {
        state.updateLayout(
            containerSize = Size(width = 100f, height = 200f),
            contentSize = Size(width = 100f, height = 50f),
        )
        state.applyTransform(zoomChange = 2f, panChange = Offset.Zero)
        state.applyTransform(zoomChange = 1f, panChange = Offset(x = 80f, y = 80f))

        assertEquals(Offset(x = 50f, y = 0f), state.offset)
    }

    @Test
    fun updateLayout_reclampsTheExistingOffset() {
        val large = Size(width = 200f, height = 200f)
        state.updateLayout(containerSize = large, contentSize = large)
        state.applyTransform(zoomChange = 3f, panChange = Offset.Zero)
        state.applyTransform(zoomChange = 1f, panChange = Offset(x = 300f, y = 300f))
        state.updateLayout(containerSize = SQUARE, contentSize = SQUARE)

        assertEquals(Offset(x = 100f, y = 100f), state.offset)
    }

    @Test
    fun applyDoubleTap_whenUnzoomed_zoomsInAroundTheTap() {
        state.updateLayout(containerSize = SQUARE, contentSize = SQUARE)
        state.applyDoubleTap(tapOffset = Offset(x = 75f, y = 25f))

        assertEquals(1.75f, state.scale)
        assertEquals(Offset(x = -18.75f, y = 18.75f), state.offset)
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

    @Test
    fun reset_clearsTheZoomOffsetAndAnimation() {
        state.updateLayout(containerSize = SQUARE, contentSize = SQUARE)
        state.applyDoubleTap(tapOffset = Offset(x = 75f, y = 25f))
        state.reset()

        assertEquals(1f, state.scale)
        assertEquals(Offset.Zero, state.offset)
        assertFalse(state.isZoomAnimationEnabled)
    }

    private companion object {
        val SQUARE = Size(width = 100f, height = 100f)
    }
}
