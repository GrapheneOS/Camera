package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderStateHolderTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val rendered = mutableListOf<ViewfinderState>()

    private val stateHolder = ViewfinderStateHolder(
        initial = ViewfinderState(mode = CameraMode.CAMERA),
        render = { state ->
            rendered += state
            ViewfinderUiState(mode = state.mode)
        },
    )

    @Test
    fun uiState_beforeAnyUpdate_isTheLoadingState() {
        assertEquals(ViewfinderUiState(), stateHolder.uiState.value)
        assertTrue(rendered.isEmpty())
    }

    @Test
    fun update_rendersTheStateItWroteBeforeReturning() {
        stateHolder.update { it.copy(mode = CameraMode.VIDEO) }

        assertEquals(CameraMode.VIDEO, stateHolder.state.value.mode)
        assertEquals(CameraMode.VIDEO, stateHolder.uiState.value.mode)
        assertEquals(listOf(ViewfinderState(mode = CameraMode.VIDEO)), rendered)
    }

    @Test
    fun update_madeWhileTheScreenRendersAnother_isTheOneLeftRendered() {
        runTest {
            backgroundScope.launch(mainDispatcherRule.testDispatcher) {
                stateHolder.uiState.collect { uiState ->
                    if (uiState.mode == CameraMode.VIDEO) {
                        stateHolder.update { it.copy(mode = CameraMode.QR_SCAN) }
                    }
                }
            }

            stateHolder.update { it.copy(mode = CameraMode.VIDEO) }

            assertEquals(CameraMode.QR_SCAN, stateHolder.state.value.mode)
            assertEquals(CameraMode.QR_SCAN, stateHolder.uiState.value.mode)
        }
    }
}
