package app.grapheneos.camera.ui.components.countdowntimer

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CountDownTimerBoldnessTest {

    private val clock = ManualFrameClock()
    private val boldness = CountDownTimerBoldness()

    @Test
    fun punch_atDoubleScale_stretchesBothPhasesTogether() {
        runTest {
            val punch = launch(clock + AnimationScale(scaleFactor = 2f)) { boldness.punch() }

            frame(millis = 0)
            frame(millis = 320)
            assertEquals(boldnessAt(elapsedMillis = 160f), boldness.value)

            frame(millis = 1_400)
            assertEquals(boldnessAt(elapsedMillis = 700f), boldness.value)

            frame(millis = 2_000)
            assertEquals(0f, boldness.value)
            assertTrue(punch.isCompleted)
        }
    }

    @Test
    fun punch_withAnimationsRemoved_staysAtRest() {
        runTest {
            val punch = launch(clock + AnimationScale(scaleFactor = 0f)) { boldness.punch() }

            frame(millis = 0)
            assertEquals(0f, boldness.value)
            assertTrue(punch.isCompleted)
        }
    }

    private fun TestScope.frame(millis: Long) {
        runCurrent()
        clock.frames.trySend(millis * NANOS_PER_MILLI)
        runCurrent()
    }

    private fun boldnessAt(elapsedMillis: Float): Float {
        return CountDownTimerBoldness.boldnessAt(
            elapsedMillis = elapsedMillis,
            startBoldness = 0f,
        )
    }

    private class ManualFrameClock : MonotonicFrameClock {

        val frames = Channel<Long>(capacity = Channel.UNLIMITED)

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            return onFrame(frames.receive())
        }
    }

    private class AnimationScale(
        override val scaleFactor: Float,
    ) : MotionDurationScale

    private companion object {
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
