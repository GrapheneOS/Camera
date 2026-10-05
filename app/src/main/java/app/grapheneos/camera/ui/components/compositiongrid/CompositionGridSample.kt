package app.grapheneos.camera.ui.components.compositiongrid

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern
import app.grapheneos.camera.ui.core.CameraPreviewControl
import app.grapheneos.camera.ui.core.CameraPreviewSample
import app.grapheneos.camera.ui.core.PREVIEW_ASPECT_RATIO
import app.grapheneos.camera.ui.core.PREVIEW_TALL_ASPECT_RATIO

private val GRID_OPTIONS = listOf(
    null,
    CompositionGridPattern.Thirds,
    CompositionGridPattern.Quarters,
    CompositionGridPattern.GoldenRatio,
)

@Preview(heightDp = 720)
@Composable
private fun CompositionGridSamplePreview() {
    CompositionGridSample()
}

@Composable
private fun CompositionGridSample() {
    val state = remember { CompositionGridSampleState() }

    CameraPreviewSample(
        status = "Taps on the image ${state.taps}",
        controls = {
            CameraPreviewControl(
                text = state.gridLabel,
                onClick = state::nextGrid,
            )
            CameraPreviewControl(
                text = state.aspectRatioLabel,
                onClick = state::toggleAspectRatio,
            )
        },
        viewfinderAspectRatio = state.aspectRatio,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(state) {
                    detectTapGestures { state.tap() }
                },
        )
        state.pattern?.let { pattern ->
            CompositionGrid(
                pattern = pattern,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Stable
private class CompositionGridSampleState {

    private var gridIndex by mutableIntStateOf(GRID_OPTIONS.indexOf(CompositionGridPattern.Thirds))
    private var isTall by mutableStateOf(false)

    var taps by mutableIntStateOf(0)
        private set

    val pattern: CompositionGridPattern?
        get() {
            return GRID_OPTIONS[gridIndex]
        }

    val gridLabel: String
        get() {
            val name = when (pattern) {
                null -> "Off"
                CompositionGridPattern.Thirds -> "3×3"
                CompositionGridPattern.Quarters -> "4×4"
                CompositionGridPattern.GoldenRatio -> "Golden ratio"
            }

            return "Grid $name"
        }

    val aspectRatio: Float
        get() {
            return when {
                isTall -> PREVIEW_TALL_ASPECT_RATIO
                else -> PREVIEW_ASPECT_RATIO
            }
        }

    val aspectRatioLabel: String
        get() {
            return when {
                isTall -> "9:16"
                else -> "3:4"
            }
        }

    fun nextGrid() {
        gridIndex = (gridIndex + 1) % GRID_OPTIONS.size
    }

    fun toggleAspectRatio() {
        isTall = !isTall
    }

    fun tap() {
        taps += 1
    }
}
