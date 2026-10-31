package app.grapheneos.camera

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.camera.ui.activities.FoldRequestGeneration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Deterministic tests for callback cancellation independent of device session timing. */
@RunWith(AndroidJUnit4::class)
class FoldRequestGenerationRegressionTest {
    @Test
    fun dualToRearToCamera_rejectsOldDualAndQueuedRearCallbacks() {
        val dual = FoldRequestGeneration()
        val rear = FoldRequestGeneration()
        val delayedRearHandoff = FoldRequestGeneration()

        val dualStart = dual.begin()
        val rearHandoff = delayedRearHandoff.begin()
        // The user leaves Rear for Camera before Android delivers either callback.
        dual.cancel()
        rear.cancel()
        delayedRearHandoff.cancel()

        assertFalse("Late Dual callback reactivated the outer preview", dual.isCurrent(dualStart))
        assertFalse("Queued Rear handoff survived Camera selection",
            delayedRearHandoff.isCurrent(rearHandoff))
        assertTrue("A new request must remain possible", dual.isCurrent(dual.begin()))
    }

    @Test
    fun pendingDualToVideo_rejectsSessionStartedAfterCancellation() {
        val dual = FoldRequestGeneration()
        val startingRequest = dual.begin()
        dual.cancel() // Mode change to VIDEO, while WindowArea callback is pending.
        assertFalse("Late Dual startup remained valid", dual.isCurrent(startingRequest))
    }

    @Test
    fun cancelledRearTransfer_doesNotBecomeCurrentAfterANewRequest() {
        val rear = FoldRequestGeneration()
        val cancelled = rear.begin()
        rear.cancel()
        val replacement = rear.begin()
        assertFalse(rear.isCurrent(cancelled))
        assertTrue(rear.isCurrent(replacement))
    }
}
