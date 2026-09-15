package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderStateHolderTest {

    private val rendered = mutableListOf<ViewfinderState>()

    private val stateHolder = ViewfinderStateHolder(
        initial = ViewfinderState(mode = CameraMode.CAMERA, requiresVideoModeOnly = false),
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
        assertEquals(
            listOf(ViewfinderState(mode = CameraMode.VIDEO, requiresVideoModeOnly = false)),
            rendered,
        )
    }
}
