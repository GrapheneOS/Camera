package app.grapheneos.camera

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LocationSelectionTest {

    private val baseNanos = TimeUnit.SECONDS.toNanos(10_000)

    private fun location(elapsedMs: Long, accuracy: Float? = null) =
        Location("test").apply {
            elapsedRealtimeNanos = baseNanos + TimeUnit.MILLISECONDS.toNanos(elapsedMs)
            if (accuracy != null) {
                this.accuracy = accuracy
            }
        }

    @Test
    fun returnsNullForEmptyList() {
        assertNull(getOptimalLocation(emptyList()))
    }

    @Test
    fun returnsNullWhenEveryElementIsNull() {
        assertNull(getOptimalLocation(listOf(null, null)))
    }

    @Test
    fun ignoresNullElements() {
        val only = location(elapsedMs = 0, accuracy = 5f)
        assertSame(only, getOptimalLocation(listOf(null, only, null)))
    }

    @Test
    fun prefersTheMoreAccurateFixWithinTheThreshold() {
        val accurate = location(elapsedMs = 0, accuracy = 5f)
        val newerButCoarser = location(elapsedMs = 5_000, accuracy = 50f)
        assertSame(accurate, getOptimalLocation(listOf(accurate, newerButCoarser)))
    }

    @Test
    fun prefersTheNewerFixWhenAccuracyIsEqual() {
        val older = location(elapsedMs = 0, accuracy = 10f)
        val newer = location(elapsedMs = 5_000, accuracy = 10f)
        assertSame(newer, getOptimalLocation(listOf(older, newer)))
    }

    @Test
    fun prefersTheNewerFixBeyondTheThresholdEvenIfLessAccurate() {
        val accurate = location(elapsedMs = 0, accuracy = 5f)
        val muchNewerButCoarse = location(elapsedMs = 12_000, accuracy = 500f)
        assertSame(muchNewerButCoarse, getOptimalLocation(listOf(accurate, muchNewerButCoarse)))
    }

    @Test
    fun treatsAFixWithoutAccuracyAsLeastAccurate() {
        val withoutAccuracy = location(elapsedMs = 1_000, accuracy = null)
        val withAccuracy = location(elapsedMs = 0, accuracy = 100f)
        assertSame(withAccuracy, getOptimalLocation(listOf(withoutAccuracy, withAccuracy)))
    }

    @Test
    fun doesNotSelectAnAccurateFixThatIsStaleRelativeToTheNewest() {
        // The newest fix (c) pulls the freshness window forward: a is now too old
        // to compete, so the choice is the more accurate of the in-window b and c.
        val a = location(elapsedMs = 0, accuracy = 5f)
        val b = location(elapsedMs = 10_000, accuracy = 50f)
        val c = location(elapsedMs = 21_000, accuracy = 300f)
        assertSame(b, getOptimalLocation(listOf(a, b, c)))
    }

    @Test
    fun doesNotDependOnTheOrderOfTheCandidates() {
        val a = location(elapsedMs = 0, accuracy = 5f)
        val b = location(elapsedMs = 10_000, accuracy = 50f)
        val c = location(elapsedMs = 21_000, accuracy = 300f)
        val fromForward = getOptimalLocation(listOf(a, b, c))
        val fromReversed = getOptimalLocation(listOf(c, b, a))
        assertSame(fromForward, fromReversed)
    }

    @Test
    fun treatsAFixExactlyAtTheThresholdAsWithinTheWindow() {
        val accurateOlder = location(elapsedMs = 0, accuracy = 5f)
        val newer = location(elapsedMs = 11_000, accuracy = 50f)
        assertSame(accurateOlder, getOptimalLocation(listOf(accurateOlder, newer)))
    }
}
