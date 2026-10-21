package app.grapheneos.camera.ui.gallery.components.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerTransformStateTest {

    private val state = ViewerTransformState(shrinkDistance = SHRINK_DISTANCE)

    private var finger = Offset.Zero

    @Test
    fun initialState_isFittedAndUntransformed() {
        assertEquals(1f, state.scale)
        assertEquals(Offset.Zero, state.offset)
        assertEquals(0f, state.rotation)
        assertFalse(state.isZoomed())
        assertFalse(state.isTransforming)
    }

    @Test
    fun pinchingOut_stopsAtTheLargestZoom() {
        state.updateLayout(containerSize = SQUARE, imageSize = SQUARE)
        state.startTransform()

        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 10f)

        assertEquals(3f, state.scale)
    }

    @Test
    fun pinchingOut_keepsThePinchCentroidInPlace() {
        state.updateLayout(containerSize = SQUARE, imageSize = SQUARE)
        state.startTransform()

        state.applyTransform(
            centroid = Offset(x = 75f, y = 75f),
            panChange = Offset.Zero,
            zoomChange = 2f,
        )

        assertEquals(Offset(x = -25f, y = -25f), state.offset)
    }

    @Test
    fun panningWhileZoomed_staysWithinTheScaledPhoto() {
        val tall = Size(width = 100f, height = 200f)
        state.updateLayout(containerSize = tall, imageSize = tall)
        state.startTransform()

        pinchAroundTheCenter(containerSize = tall, zoomChange = 3f)
        pinchAroundTheCenter(
            containerSize = tall,
            panChange = Offset(x = 250f, y = -250f),
        )

        assertEquals(Offset(x = 100f, y = -200f), state.offset)
    }

    @Test
    fun panningALetterboxedPhoto_doesNotRevealTheBars() {
        state.updateLayout(
            containerSize = LETTERBOX_CONTAINER,
            imageSize = Size(width = 100f, height = 50f),
        )
        state.startTransform()

        pinchAroundTheCenter(containerSize = LETTERBOX_CONTAINER, zoomChange = 2f)
        pinchAroundTheCenter(
            containerSize = LETTERBOX_CONTAINER,
            panChange = Offset(x = 80f, y = 80f),
        )

        assertEquals(Offset(x = 50f, y = 0f), state.offset)
    }

    @Test
    fun pinchingOut_doesNotRotateThePhoto() {
        state.updateLayout(containerSize = SQUARE, imageSize = SQUARE)
        state.startTransform()

        pinchAroundTheCenter(
            containerSize = SQUARE,
            zoomChange = 2f,
            rotationChange = 15f,
        )

        assertEquals(0f, state.rotation)
    }

    @Test
    fun updateLayout_keepsTheZoomedPhotoWithinTheNewSize() {
        val large = Size(width = 200f, height = 200f)
        state.updateLayout(containerSize = large, imageSize = large)
        state.startTransform()

        pinchAroundTheCenter(containerSize = large, zoomChange = 3f)
        pinchAroundTheCenter(
            containerSize = large,
            panChange = Offset(x = 300f, y = 300f),
        )

        state.release()
        state.updateLayout(containerSize = SQUARE, imageSize = large)

        assertEquals(Offset(x = 100f, y = 100f), state.offset)
    }

    @Test
    fun applyTransform_whenTheLastFingerLifts_keepsTheTransform() {
        state.updateLayout(containerSize = SQUARE, imageSize = SQUARE)
        state.startTransform()

        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 2f)

        state.applyTransform(
            centroid = Offset.Unspecified,
            panChange = Offset.Zero,
        )

        assertEquals(2f, state.scale)
        assertEquals(Offset.Zero, state.offset)
    }

    @Test
    fun applyDoubleTap_whenFitted_zoomsInAroundTheTap() {
        state.updateLayout(containerSize = SQUARE, imageSize = SQUARE)
        state.applyDoubleTap(position = Offset(x = 75f, y = 25f))

        assertEquals(2.5f, state.scale)
        assertEquals(Offset(x = -37.5f, y = 37.5f), state.offset)
    }

    @Test
    fun applyDoubleTap_whenZoomed_zoomsBackOut() {
        state.updateLayout(containerSize = SQUARE, imageSize = SQUARE)
        state.applyDoubleTap(position = Offset(x = 75f, y = 25f))
        state.applyDoubleTap(position = Offset(x = 50f, y = 50f))

        assertEquals(1f, state.scale)
        assertEquals(Offset.Zero, state.offset)
    }

    @Test
    fun withoutAnImage_neitherPinchNorDoubleTapZoomsIn() {
        state.updateLayout(containerSize = SQUARE, imageSize = null)
        state.applyDoubleTap(position = SQUARE.center)
        state.startTransform()

        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 2f)

        assertEquals(1f, state.scale)
    }

    @Test
    fun drag_followsTheFingerOnBothAxes() {
        state.startDrag()

        move(panChange = Offset(x = -30f, y = 40f))

        assertEquals(Offset(x = -30f, y = 40f), state.offset)
    }

    @Test
    fun drag_shrinksThePhotoWithTheDistanceTravelled() {
        state.startDrag()

        move(panChange = Offset(x = 0f, y = SHRINK_DISTANCE / 4))
        val nearer = state.scale
        move(panChange = Offset(x = 0f, y = SHRINK_DISTANCE / 4))

        assertTrue(state.scale < nearer)
        assertTrue(nearer < 1f)
    }

    @Test
    fun drag_beyondTheShrinkDistance_shrinksNoFurther() {
        state.startDrag()

        move(panChange = Offset(x = 0f, y = SHRINK_DISTANCE))
        val atTheDistance = state.scale
        move(panChange = Offset(x = 0f, y = SHRINK_DISTANCE))

        assertEquals(atTheDistance, state.scale)
    }

    @Test
    fun drag_backUpPastTheStart_shrinksThePhotoAgain() {
        state.startDrag()

        move(panChange = Offset(x = 0f, y = SHRINK_DISTANCE / 2))
        move(panChange = Offset(x = 0f, y = -SHRINK_DISTANCE))

        assertTrue(state.scale < 1f)
    }

    @Test
    fun pinchingInDuringADrag_shrinksBelowTheDragLimit() {
        state.startDrag()

        move(panChange = Offset(x = 0f, y = SHRINK_DISTANCE))
        val dragLimit = state.scale
        move(panChange = Offset.Zero, zoomChange = 0.5f)

        assertTrue(state.scale < dragLimit)
    }

    @Test
    fun twistingDuringADrag_rotatesThePhoto() {
        state.startDrag()

        move(panChange = Offset.Zero, rotationChange = 15f)

        assertEquals(15f, state.rotation)
    }

    @Test
    fun pinchingInFromFit_shrinksAndRotatesThePhoto() {
        state.updateLayout(containerSize = SQUARE, imageSize = SQUARE)
        state.startTransform()

        pinchAroundTheCenter(
            containerSize = SQUARE,
            zoomChange = 0.8f,
            rotationChange = 15f,
        )

        assertEquals(0.8f, state.scale)
        assertEquals(15f, state.rotation)
    }

    @Test
    fun pinchingInThenOut_zoomsInKeepingTheAngle() {
        state.updateLayout(containerSize = SQUARE, imageSize = SQUARE)
        state.startTransform()

        pinchAroundTheCenter(
            containerSize = SQUARE,
            zoomChange = 0.8f,
            rotationChange = 15f,
        )
        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 2.5f)

        assertEquals(2f, state.scale)
        assertEquals(15f, state.rotation)
    }

    @Test
    fun pinchingOutThenIn_stopsAtFit() {
        state.updateLayout(containerSize = SQUARE, imageSize = SQUARE)
        state.startTransform()

        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 2f)
        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 0.4f)

        state.release()

        assertEquals(1f, state.scale)
        assertFalse(state.isDismissed)
    }

    @Test
    fun pinchingInWhileZoomed_stopsAtFit() {
        state.updateLayout(containerSize = SQUARE, imageSize = SQUARE)
        state.applyDoubleTap(position = SQUARE.center)
        state.startTransform()

        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 0.2f)

        state.release()

        assertEquals(1f, state.scale)
        assertFalse(state.isDismissed)
    }

    @Test
    fun pinchingInThenOutThenIn_shrinksBelowFitAgain() {
        state.updateLayout(containerSize = SQUARE, imageSize = SQUARE)
        state.startTransform()

        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 0.8f)
        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 2.5f)
        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 0.4f)

        assertEquals(0.8f, state.scale)
    }

    @Test
    fun fadeProgress_holdsTheDeepestShrinkWhilePinchingBackOut() {
        state.updateLayout(containerSize = SQUARE, imageSize = SQUARE)
        state.startTransform()

        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 0.9f)
        val deepestFade = state.fadeProgress()
        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 2f)

        assertTrue(deepestFade > 0f)
        assertEquals(deepestFade, state.fadeProgress())
    }

    @Test
    fun fadeProgress_afterTheReturn_isNone() {
        state.startDrag()

        move(panChange = Offset(x = 0f, y = SHRINK_DISTANCE / 4))

        state.release()

        assertEquals(0f, state.fadeProgress())
    }

    @Test
    fun fadeProgress_afterDismissing_isComplete() {
        state.startDrag()

        move(panChange = Offset(x = 0f, y = SHRINK_DISTANCE))

        state.release()

        assertEquals(1f, state.fadeProgress())
    }

    @Test
    fun fadeProgress_whileZooming_isNone() {
        state.updateLayout(containerSize = SQUARE, imageSize = SQUARE)
        state.startTransform()

        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 2f)
        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 0.4f)

        assertEquals(0f, state.fadeProgress())
    }

    @Test
    fun isZoomStoppedAtFit_onlyWhenAZoomReachesFit() {
        state.updateLayout(containerSize = SQUARE, imageSize = SQUARE)
        state.startTransform()

        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 2f)
        assertFalse(state.isZoomStoppedAtFit())
        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 0.4f)

        assertTrue(state.isZoomStoppedAtFit())
    }

    @Test
    fun isZoomStoppedAtFit_neverWhileTheGestureCanClose() {
        state.updateLayout(containerSize = SQUARE, imageSize = SQUARE)
        state.startTransform()

        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 0.8f)
        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 1.25f)

        assertFalse(state.isZoomStoppedAtFit())
    }

    @Test
    fun release_whileZoomedAfterPinchingIn_keepsTheZoomAndStraightensThePhoto() {
        state.updateLayout(containerSize = SQUARE, imageSize = SQUARE)
        state.startTransform()

        pinchAroundTheCenter(
            containerSize = SQUARE,
            zoomChange = 0.8f,
            rotationChange = 15f,
        )
        pinchAroundTheCenter(containerSize = SQUARE, zoomChange = 2.5f)

        state.release()

        assertFalse(state.isDismissed)
        assertEquals(2f, state.scale)
        assertEquals(0f, state.rotation)
    }

    @Test
    fun release_pastHalfTheWayToTheSmallestDragSize_dismisses() {
        state.startDrag()

        move(panChange = Offset(x = 0f, y = SHRINK_DISTANCE * 3 / 4))

        state.release()

        assertTrue(state.isDismissed)
        assertFalse(state.isTransforming)
    }

    @Test
    fun release_shortOfHalfTheWay_returnsThePhoto() {
        state.startDrag()

        move(panChange = Offset(x = 0f, y = SHRINK_DISTANCE / 4))

        state.release()

        assertFalse(state.isDismissed)
        assertEquals(Offset.Zero, state.offset)
        assertEquals(1f, state.scale)
        assertEquals(0f, state.rotation)
    }

    @Test
    fun release_afterADragEndingDownwards_exitsDownwards() {
        state.startDrag()

        move(panChange = Offset(x = 0f, y = -SHRINK_DISTANCE))
        move(panChange = Offset(x = 30f, y = 10f))

        state.release()

        assertTrue(state.isDismissed)
        assertEquals(1f, state.exitDirection)
    }

    @Test
    fun release_afterADragEndingUpwards_exitsUpwards() {
        state.startDrag()

        move(panChange = Offset(x = 0f, y = SHRINK_DISTANCE))
        move(panChange = Offset(x = 0f, y = -10f))

        state.release()

        assertTrue(state.isDismissed)
        assertEquals(-1f, state.exitDirection)
    }

    @Test
    fun drag_sidewaysOnly_keepsTheExitDirection() {
        state.startDrag()

        move(panChange = Offset(x = 0f, y = -10f))
        move(panChange = Offset(x = 30f, y = 0f))

        assertEquals(-1f, state.exitDirection)
    }

    @Test
    fun release_afterPinchingIn_exitsDownwardsWhereverTheFingersWent() {
        state.startTransform()

        move(panChange = Offset(x = 0f, y = -10f), zoomChange = 0.5f)

        state.release()

        assertTrue(state.isDismissed)
        assertEquals(1f, state.exitDirection)
    }

    @Test
    fun cancel_returnsThePhotoWithoutDismissing() {
        state.startDrag()

        move(panChange = Offset(x = 0f, y = SHRINK_DISTANCE))

        state.cancel()

        assertFalse(state.isDismissed)
        assertFalse(state.isTransforming)
        assertEquals(1f, state.scale)
    }

    private fun move(
        panChange: Offset,
        zoomChange: Float = 1f,
        rotationChange: Float = 0f,
    ) {
        state.applyTransform(
            centroid = finger,
            panChange = panChange,
            zoomChange = zoomChange,
            rotationChange = rotationChange,
        )
        finger += panChange
    }

    private fun pinchAroundTheCenter(
        containerSize: Size,
        zoomChange: Float = 1f,
        rotationChange: Float = 0f,
        panChange: Offset = Offset.Zero,
    ) {
        state.applyTransform(
            centroid = containerSize.center,
            panChange = panChange,
            zoomChange = zoomChange,
            rotationChange = rotationChange,
        )
    }

    private companion object {
        const val SHRINK_DISTANCE = 96f
        val SQUARE = Size(width = 100f, height = 100f)
        val LETTERBOX_CONTAINER = Size(width = 100f, height = 200f)
    }
}
