package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.testutil.viewfinderStateHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ViewfinderStateHolderTest {

    private val stateHolder = viewfinderStateHolder(mode = CameraMode.CAMERA)

    @Test
    fun derive_startsFromTheCurrentState() {
        val mode = stateHolder.derive { state -> state.mode }

        assertEquals(CameraMode.CAMERA, mode.value)
    }

    @Test
    fun update_rederivesTheStateItWroteBeforeReturning() {
        val mode = stateHolder.derive { state -> state.mode }

        stateHolder.update { state -> state.copy(mode = CameraMode.VIDEO) }

        assertEquals(CameraMode.VIDEO, stateHolder.state.value.mode)
        assertEquals(CameraMode.VIDEO, mode.value)
    }

    @Test
    fun update_ofAnotherPartOfTheState_doesNotEmitTheDerivation() {
        runTest {
            val modes = mutableListOf<CameraMode>()
            val mode = stateHolder.derive { state -> state.mode }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                mode.collect { mode -> modes += mode }
            }

            stateHolder.update { state -> state.copy(keepsScreenAwake = true) }
            stateHolder.update { state -> state.copy(mode = CameraMode.VIDEO) }

            assertEquals(listOf(CameraMode.CAMERA, CameraMode.VIDEO), modes)
        }
    }
}
