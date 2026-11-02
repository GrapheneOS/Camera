package app.grapheneos.camera.ui.core.screen

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StateHolderTest {

    private val stateHolder = StateHolder(initial = TestState())

    @Test
    fun derive_startsFromTheCurrentState() {
        stateHolder.update { state -> state.copy(count = 2) }

        val count = stateHolder.derive { state -> state.count }

        assertEquals(2, count.value)
    }

    @Test
    fun update_rederivesTheStateItWroteBeforeReturning() {
        val count = stateHolder.derive { state -> state.count }

        stateHolder.update { state -> state.copy(count = 1) }

        assertEquals(1, stateHolder.state.value.count)
        assertEquals(1, count.value)
    }

    @Test
    fun update_ofAnotherPartOfTheState_doesNotEmitTheDerivation() {
        runTest {
            val counts = mutableListOf<Int>()
            val count = stateHolder.derive { state -> state.count }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                count.collect { count -> counts += count }
            }

            stateHolder.update { state -> state.copy(isBusy = true) }
            stateHolder.update { state -> state.copy(count = 1) }

            assertEquals(listOf(0, 1), counts)
        }
    }

    private data class TestState(
        val count: Int = 0,
        val isBusy: Boolean = false,
    )
}
