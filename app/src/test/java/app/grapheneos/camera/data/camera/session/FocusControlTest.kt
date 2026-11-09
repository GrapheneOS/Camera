package app.grapheneos.camera.data.camera.session

import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.FocusMeteringResult
import androidx.camera.core.MeteringPointFactory
import app.grapheneos.camera.data.camera.model.CameraFocusEvent
import app.grapheneos.camera.data.camera.model.CameraFocusRequest
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.ExecutionException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FocusControlTest {

    private val focusMeteringResult = mockk<FocusMeteringResult> {
        every { isFocusSuccessful } returns true
    }
    private val resultListener = slot<Runnable>()
    private val result = mockk<ListenableFuture<FocusMeteringResult>> {
        every { addListener(capture(resultListener), any()) } just runs
        every { isCancelled } returns false
        every { get() } returns focusMeteringResult
    }
    private val action = slot<FocusMeteringAction>()
    private val control = mockk<CameraControl> {
        every { startFocusAndMetering(capture(action)) } returns result
        every { cancelFocusAndMetering() } returns mockk(relaxed = true)
    }
    private val meteringPointFactory = mockk<MeteringPointFactory> {
        every { createPoint(any(), any()) } returns mockk(relaxed = true)
    }
    private val events = mutableListOf<CameraFocusEvent>()
    private var requestId = 0L

    @Test
    fun focus_metersTheTappedPointWithoutCameraXAutoCancel() {
        runTest {
            focus(createFocusControl())

            verify(exactly = 1) { meteringPointFactory.createPoint(10f, 20f) }
            assertFalse(action.captured.isAutoCancelEnabled)
            assertEquals(listOf(CameraFocusEvent.Started(requestId = 1)), events)
        }
    }

    @Test
    fun successfulResult_reportsFocus() {
        runTest {
            focus(createFocusControl())

            resultListener.captured.run()

            assertEquals(CameraFocusEvent.Focused(requestId = 1), events.last())
        }
    }

    @Test
    fun unsuccessfulResult_reportsNothing() {
        runTest {
            every { focusMeteringResult.isFocusSuccessful } returns false
            focus(createFocusControl())

            resultListener.captured.run()

            assertEquals(listOf(CameraFocusEvent.Started(requestId = 1)), events)
        }
    }

    @Test
    fun cameraCancellation_reportsCancellation() {
        runTest {
            val cancellation = CameraControl.OperationCanceledException("Cancelled")
            every { result.get() } throws ExecutionException(cancellation)
            focus(createFocusControl())

            resultListener.captured.run()

            assertEquals(CameraFocusEvent.Cancelled(requestId = 1), events.last())
        }
    }

    @Test
    fun cancelledResult_reportsCancellation() {
        runTest {
            every { result.isCancelled } returns true
            focus(createFocusControl())

            resultListener.captured.run()

            assertEquals(CameraFocusEvent.Cancelled(requestId = 1), events.last())
        }
    }

    @Test
    fun otherCameraFailure_keepsTheRequestWithoutReportingFocus() {
        runTest {
            every { result.get() } throws ExecutionException(IllegalArgumentException())
            focus(createFocusControl())

            resultListener.captured.run()

            assertEquals(listOf(CameraFocusEvent.Started(requestId = 1)), events)
        }
    }

    @Test
    fun replacedRequest_ignoresItsLateResult() {
        runTest {
            val focusControl = createFocusControl()
            focus(focusControl)
            val firstResultListener = resultListener.captured
            focus(focusControl)
            val beforeLateResult = events.toList()

            firstResultListener.run()

            assertEquals(beforeLateResult, events)
        }
    }

    @Test
    fun cancelledRequest_ignoresItsLateResult() {
        runTest {
            val focusControl = createFocusControl()
            focus(focusControl)
            focusControl.cancel()
            val beforeLateResult = events.toList()

            resultListener.captured.run()

            assertEquals(beforeLateResult, events)
            assertEquals(CameraFocusEvent.Cancelled(requestId = 1), events.last())
        }
    }

    @Test
    fun disabledTimeout_keepsTheRequestUntilItIsCancelled() {
        runTest {
            val focusControl = createFocusControl()
            focus(focusControl = focusControl, timeout = Duration.ZERO)

            advanceTimeBy(60.seconds)

            assertEquals(1, events.size)
            verify(exactly = 0) { control.cancelFocusAndMetering() }

            focusControl.cancel()

            verify(exactly = 1) { control.cancelFocusAndMetering() }
            assertEquals(CameraFocusEvent.Cancelled(requestId = 1), events.last())
        }
    }

    @Test
    fun configuredTimeout_cancelsMeteringAndReportsTheCancellation() {
        runTest {
            focus(createFocusControl())

            advanceTimeBy(4999.milliseconds)

            verify(exactly = 0) { control.cancelFocusAndMetering() }

            advanceTimeBy(1.milliseconds)
            runCurrent()

            verify(exactly = 1) { control.cancelFocusAndMetering() }
            assertEquals(CameraFocusEvent.Cancelled(requestId = 1), events.last())
        }
    }

    @Test
    fun replacedRequest_cannotBeCancelledByThePreviousTimeout() {
        runTest {
            val focusControl = createFocusControl()
            focus(focusControl)
            advanceTimeBy(4.seconds)
            focus(focusControl = focusControl, timeout = Duration.ZERO)

            advanceTimeBy(60.seconds)

            verify(exactly = 0) { control.cancelFocusAndMetering() }
            assertEquals(
                listOf(1L),
                events.filterIsInstance<CameraFocusEvent.Cancelled>().map { it.requestId },
            )
        }
    }

    @Test
    fun locking_keepsTheRequestIdAndIgnoresTheResultBeforeTheLock() {
        runTest {
            val focusControl = createFocusControl()
            focus(focusControl)
            val resultBeforeLock = resultListener.captured

            focusControl.lock()
            resultBeforeLock.run()

            assertEquals(CameraFocusEvent.Locked(requestId = 1), events.last())
            assertFalse(action.captured.isAutoCancelEnabled)

            resultListener.captured.run()
            advanceTimeBy(60.seconds)

            assertEquals(CameraFocusEvent.Focused(requestId = 1), events.last())
            verify(exactly = 0) { control.cancelFocusAndMetering() }
        }
    }

    @Test
    fun detachedControl_cancelsTheRequestWithoutTouchingTheCamera() {
        runTest {
            val focusControl = createFocusControl()
            focusControl.detach()

            focus(focusControl)

            assertEquals(listOf(CameraFocusEvent.Cancelled(requestId = 1)), events)
            verify(exactly = 0) { control.startFocusAndMetering(any()) }
        }
    }

    @Test
    fun detach_reportsCancellationWithoutCancellingTheCamera() {
        runTest {
            val focusControl = createFocusControl()
            focus(focusControl)

            focusControl.detach()
            resultListener.captured.run()

            assertEquals(CameraFocusEvent.Cancelled(requestId = 1), events.last())
            verify(exactly = 0) { control.cancelFocusAndMetering() }
        }
    }

    private fun TestScope.createFocusControl(): FocusControl {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val focusControl = FocusControlImpl(
            mainDispatcher = dispatcher,
            scope = backgroundScope,
        )
        backgroundScope.launch(dispatcher) {
            focusControl.events.collect { event -> events += event }
        }
        focusControl.attach(
            control = control,
            meteringPointFactory = meteringPointFactory,
        )
        return focusControl
    }

    private fun focus(
        focusControl: FocusControl,
        timeout: Duration = 5.seconds,
    ) {
        requestId += 1
        focusControl.focus(
            request = CameraFocusRequest(
                id = requestId,
                x = 10f,
                y = 20f,
                autoCancelAfter = timeout,
            ),
        )
    }
}
