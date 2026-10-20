package app.grapheneos.camera.ui.gallery.components.gesture

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DismissGestureStateTest {

    private val state = DismissGestureState(shrinkDistance = SHRINK_DISTANCE)

    @Test
    fun start_remembersWhereTheFingerTouched() {
        state.start(origin = Offset(x = 120f, y = 300f))

        assertEquals(Offset(x = 120f, y = 300f), state.origin)
        assertTrue(state.isDragging)
    }

    @Test
    fun applyTransform_followsTheFingerOnBothAxes() {
        state.applyTransform(panChange = Offset(x = -30f, y = 40f))

        assertEquals(Offset(x = -30f, y = 40f), state.offset)
    }

    @Test
    fun applyTransform_shrinksThePhotoWithTheDistanceTravelled() {
        state.applyTransform(panChange = Offset(x = 0f, y = SHRINK_DISTANCE / 4))
        val nearer = state.scale

        state.applyTransform(panChange = Offset(x = 0f, y = SHRINK_DISTANCE / 4))

        assertTrue(state.scale < nearer)
        assertTrue(nearer < 1f)
    }

    @Test
    fun applyTransform_beyondTheShrinkDistance_shrinksNoFurther() {
        state.applyTransform(panChange = Offset(x = 0f, y = SHRINK_DISTANCE))
        val atTheDistance = state.scale

        state.applyTransform(panChange = Offset(x = 0f, y = SHRINK_DISTANCE))

        assertEquals(atTheDistance, state.scale)
    }

    @Test
    fun applyTransform_backUpPastTheStart_shrinksThePhotoAgain() {
        state.applyTransform(panChange = Offset(x = 0f, y = SHRINK_DISTANCE / 2))
        state.applyTransform(panChange = Offset(x = 0f, y = -SHRINK_DISTANCE))

        assertTrue(state.scale < 1f)
    }

    @Test
    fun applyTransform_pinchingIn_shrinksBelowTheDragLimit() {
        val dragLimit = state.scale

        state.applyTransform(
            panChange = Offset(x = 0f, y = SHRINK_DISTANCE),
        )
        state.applyTransform(
            panChange = Offset.Zero,
            zoomChange = 0.5f,
        )

        assertTrue(state.scale < dragLimit)
    }

    @Test
    fun applyTransform_pinchingOut_growsBackNoFurtherThanTheFullSize() {
        state.applyTransform(
            panChange = Offset(x = 0f, y = SHRINK_DISTANCE),
        )
        state.applyTransform(
            panChange = Offset.Zero,
            zoomChange = 4f,
        )

        assertEquals(1f, state.scale)
    }

    @Test
    fun applyTransform_twisting_rotatesThePhoto() {
        state.applyTransform(
            panChange = Offset.Zero,
            rotationChange = 15f,
        )

        assertEquals(15f, state.rotation)
    }

    @Test
    fun release_pastHalfTheWayToTheSmallestSize_dismisses() {
        state.start(origin = Offset.Zero)
        state.applyTransform(panChange = Offset(x = 0f, y = SHRINK_DISTANCE * 3 / 4))

        state.release()

        assertTrue(state.isDismissed)
        assertFalse(state.isDragging)
    }

    @Test
    fun release_afterAMoveDown_exitsDownwards() {
        state.applyTransform(panChange = Offset(x = 0f, y = -SHRINK_DISTANCE))
        state.applyTransform(panChange = Offset(x = 30f, y = 10f))

        state.release()

        assertTrue(state.isDismissed)
        assertEquals(1f, state.exitDirection)
    }

    @Test
    fun release_afterAMoveUp_exitsUpwards() {
        state.applyTransform(panChange = Offset(x = 0f, y = SHRINK_DISTANCE))
        state.applyTransform(panChange = Offset(x = 0f, y = -10f))

        state.release()

        assertTrue(state.isDismissed)
        assertEquals(-1f, state.exitDirection)
    }

    @Test
    fun applyTransform_sidewaysOnly_keepsTheExitDirection() {
        state.applyTransform(panChange = Offset(x = 0f, y = -10f))
        state.applyTransform(panChange = Offset(x = 30f, y = 0f))

        assertEquals(-1f, state.exitDirection)
    }

    @Test
    fun release_whenDismissedByPinchAlone_exitsDownwards() {
        state.applyTransform(
            panChange = Offset.Zero,
            zoomChange = 0.5f,
        )

        state.release()

        assertTrue(state.isDismissed)
        assertEquals(1f, state.exitDirection)
    }

    @Test
    fun release_shortOfHalfTheWay_returnsThePhoto() {
        state.applyTransform(panChange = Offset(x = 0f, y = SHRINK_DISTANCE / 4))

        state.release()

        assertFalse(state.isDismissed)
        assertEquals(Offset.Zero, state.offset)
        assertEquals(1f, state.scale)
        assertEquals(0f, state.rotation)
    }

    @Test
    fun release_afterPinchingBackUp_returnsThePhoto() {
        state.applyTransform(panChange = Offset(x = 0f, y = SHRINK_DISTANCE))
        state.applyTransform(
            panChange = Offset.Zero,
            zoomChange = 1.5f,
        )

        state.release()

        assertFalse(state.isDismissed)
    }

    @Test
    fun cancel_returnsThePhotoWithoutDismissing() {
        state.start(origin = Offset.Zero)
        state.applyTransform(panChange = Offset(x = 0f, y = SHRINK_DISTANCE))

        state.cancel()

        assertFalse(state.isDismissed)
        assertFalse(state.isDragging)
        assertEquals(1f, state.scale)
    }

    @Test
    fun progressAt_isNoneAtFullSizeAndCompleteAtTheSmallestDragSize() {
        assertEquals(0f, state.progressAt(scale = 1f))
        assertEquals(1f, state.progressAt(scale = 0.5f))
    }

    private companion object {
        const val SHRINK_DISTANCE = 96f
    }
}
