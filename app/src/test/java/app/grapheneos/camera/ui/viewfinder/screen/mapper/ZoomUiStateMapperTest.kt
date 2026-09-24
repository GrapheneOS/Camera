package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderSessionState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ZoomUiState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ZoomUiStateMapperTest {

    private val mapper: ZoomUiStateMapper = ZoomUiStateMapperImpl()

    @Test
    fun zoom_isPublishedForTheZoomBar() {
        val state = stateWith(
            zoom = CameraZoom(
                zoomRatio = 2f,
                linearZoom = 0.5f,
                minZoomRatio = 1f,
                maxZoomRatio = 10f,
            ),
        )

        assertEquals(ZoomUiState(zoomRatio = 2f, linearZoom = 0.5f), mapper.map(state))
    }

    @Test
    fun zoom_beforeTheCameraIsBound_isTheDefault() {
        assertEquals(ZoomUiState(), mapper.map(stateWith(zoom = null)))
    }

    private fun stateWith(zoom: CameraZoom?): ViewfinderState {
        return ViewfinderState(
            mode = CameraMode.CAMERA,
            requiresVideoModeOnly = false,
            session = ViewfinderSessionState(zoom = zoom),
        )
    }
}
