package app.grapheneos.camera.ui.previewoverlays.screen.mapper

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewContentState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewOverlaysUiStateMapperTest {

    private val mapper: PreviewOverlaysUiStateMapper = PreviewOverlaysUiStateMapperImpl()

    @Test
    fun beforePreviewContentArrives_showsNothing() {
        val state = mapper.map(PreviewOverlaysState(gridType = GridType.THREE_BY_THREE))

        assertEquals(PreviewOverlaysUiState(), state)
    }

    @Test
    fun gridNone_showsNoGrid() {
        val state = mapper.map(
            PreviewOverlaysState(previewContent = PreviewContentState()),
        )

        assertFalse(state.isGridVisible)
    }

    @Test
    fun photoAndVideoModes_useEachEnabledGridPattern() {
        val patterns = mapOf(
            GridType.THREE_BY_THREE to CompositionGridPattern.Thirds,
            GridType.FOUR_BY_FOUR to CompositionGridPattern.Quarters,
            GridType.GOLDEN_RATIO to CompositionGridPattern.GoldenRatio,
        )

        val modes = listOf(
            CameraMode.CAMERA,
            CameraMode.VIDEO,
            CameraMode.AUTO,
            CameraMode.FACE_RETOUCH,
            CameraMode.PORTRAIT,
            CameraMode.NIGHT,
            CameraMode.HDR,
        )

        modes.forEach { mode ->
            patterns.forEach { (gridType, pattern) ->
                val state = mapper.map(
                    PreviewOverlaysState(
                        gridType = gridType,
                        previewContent = PreviewContentState(mode = mode),
                    ),
                )

                assertTrue(state.isGridVisible)
                assertEquals(pattern, state.gridPattern)
            }
        }
    }

    @Test
    fun qrTransitionAndCaptureResult_eachHideEveryGrid() {
        val previewStates = listOf(
            PreviewContentState(mode = CameraMode.QR_SCAN),
            PreviewContentState(isTransitionFrameVisible = true),
            PreviewContentState(isCaptureResultVisible = true),
        )

        previewStates.forEach { content ->
            GridType.entries.forEach { gridType ->
                val state = mapper.map(
                    PreviewOverlaysState(
                        gridType = gridType,
                        previewContent = content,
                    ),
                )

                assertFalse(state.isGridVisible)
            }
        }
    }

    @Test
    fun previewFlash_hidesTheGridUntilItEnds() {
        val state = PreviewOverlaysState(
            gridType = GridType.GOLDEN_RATIO,
            previewContent = PreviewContentState(),
            isPreviewFlashVisible = true,
        )

        assertFalse(mapper.map(state).isGridVisible)
        assertTrue(mapper.map(state.copy(isPreviewFlashVisible = false)).isGridVisible)
    }
}
