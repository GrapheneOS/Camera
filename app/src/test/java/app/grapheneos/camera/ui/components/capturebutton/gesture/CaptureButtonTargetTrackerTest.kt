package app.grapheneos.camera.ui.components.capturebutton.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonDirection
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTarget
import app.grapheneos.camera.ui.core.PREVIEW_LOCK_ICON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureButtonTargetTrackerTest {

    private val start = CaptureButtonTarget(
        direction = CaptureButtonDirection.Start,
        icon = PREVIEW_LOCK_ICON,
        accessibilityLabel = "Lock",
        distance = 100.dp,
    )
    private val tracker = CaptureButtonTargetTracker(
        targets = listOf(start),
        density = Density(density = 1f),
        layoutDirection = LayoutDirection.Ltr,
    )

    @Test
    fun update_shortOfTheTarget_armsNothing() {
        assertNull(tracker.update(Offset(x = -90f, y = 0f)))
    }

    @Test
    fun update_atTheTarget_armsIt() {
        assertEquals(start, tracker.update(Offset(x = -100f, y = 0f)))
    }

    @Test
    fun update_slightlyBackFromAnArmedTarget_keepsItArmed() {
        tracker.update(Offset(x = -100f, y = 0f))

        assertEquals(start, tracker.update(Offset(x = -85f, y = 0f)))
    }

    @Test
    fun update_wellBackFromAnArmedTarget_disarmsIt() {
        tracker.update(Offset(x = -100f, y = 0f))

        assertNull(tracker.update(Offset(x = -70f, y = 0f)))
    }

    @Test
    fun update_inTheOppositeDirection_armsNothing() {
        assertNull(tracker.update(Offset(x = 200f, y = 0f)))
    }

    @Test
    fun leadsToTarget_swipedTowardsTheTarget_isTrue() {
        assertTrue(tracker.leadsToTarget(offset = Offset(x = -20f, y = 5f), touchSlop = SLOP))
    }

    @Test
    fun leadsToTarget_withinTheSlop_isFalse() {
        assertFalse(tracker.leadsToTarget(offset = Offset(x = -5f, y = 0f), touchSlop = SLOP))
    }

    @Test
    fun leadsToTarget_swipedMostlyAcross_isFalse() {
        assertFalse(tracker.leadsToTarget(offset = Offset(x = -20f, y = 40f), touchSlop = SLOP))
    }

    @Test
    fun leadsToTarget_swipedAway_isFalse() {
        assertFalse(tracker.leadsToTarget(offset = Offset(x = 40f, y = 0f), touchSlop = SLOP))
    }

    private companion object {
        private const val SLOP = 10f
    }
}
