package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ViewfinderScreenWakeDelegateTest {

    private val stateHolder = ViewfinderStateHolder(
        initial = ViewfinderState(
            mode = CameraMode.CAMERA,
            requiresVideoModeOnly = false,
        ),
    )

    private val delegate = ViewfinderScreenWakeDelegateImpl()

    @Test
    fun screenWake_isHeldUntilTheScreenGoesIdle() {
        runTest {
            keepScreenAwake()
            assertTrue(keepsScreenAwake())

            advanceTimeBy(AWAKE_DURATION - 1.milliseconds)
            assertTrue(keepsScreenAwake())

            advanceTimeBy(2.milliseconds)
            assertFalse(keepsScreenAwake())
        }
    }

    @Test
    fun interaction_restartsTheIdleTimeout() {
        runTest {
            keepScreenAwake()
            advanceTimeBy(AWAKE_DURATION - 1.milliseconds)

            delegate.onScreenInteracted()
            advanceTimeBy(AWAKE_DURATION - 1.milliseconds)
            assertTrue(keepsScreenAwake())

            advanceTimeBy(2.milliseconds)
            assertFalse(keepsScreenAwake())
        }
    }

    @Test
    fun interaction_afterTheScreenWentIdle_holdsTheWakeAgain() {
        runTest {
            keepScreenAwake()
            advanceTimeBy(AWAKE_DURATION + 1.milliseconds)

            delegate.onScreenInteracted()

            assertTrue(keepsScreenAwake())
        }
    }

    @Test
    fun screenWake_isReleasedWhenNoLongerKept() {
        runTest {
            val wake = keepScreenAwake()

            wake.cancel()

            assertFalse(keepsScreenAwake())
        }
    }

    private fun keepsScreenAwake(): Boolean {
        return stateHolder.state.value.keepsScreenAwake
    }

    private fun TestScope.keepScreenAwake(): Job {
        delegate.bind(stateHolder)

        return backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            delegate.keepScreenAwake()
        }
    }

    private companion object {
        val AWAKE_DURATION = 5.minutes
    }
}
