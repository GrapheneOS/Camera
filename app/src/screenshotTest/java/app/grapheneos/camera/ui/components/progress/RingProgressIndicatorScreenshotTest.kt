package app.grapheneos.camera.ui.components.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.progress.model.RingProgress
import app.grapheneos.camera.ui.components.squarebutton.SQUARE_BUTTON_SHAPE
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PreviewLightDark
@Composable
private fun RingProgressIndicatorCircle() {
    CameraPreviewColumn {
        RingProgressIndicatorKinds(shape = CircleShape)
    }
}

@PreviewTest
@PreviewLightDark
@Composable
private fun RingProgressIndicatorRoundedSquare() {
    CameraPreviewColumn {
        RingProgressIndicatorKinds(shape = SQUARE_BUTTON_SHAPE)
    }
}

@Composable
private fun RingProgressIndicatorKinds(
    shape: CornerBasedShape,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
        verticalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        RingProgressIndicator(
            progress = RingProgress.Segmented(
                segments = 3,
                fraction = { 2f / 3 },
            ),
            shape = shape,
        )
        RingProgressIndicator(
            progress = RingProgress.Segmented(
                segments = 3,
                fraction = { 0.35f },
            ),
            shape = shape,
        )
        RingProgressIndicator(
            progress = RingProgress.Segmented(
                segments = 10,
                fraction = { 0.75f },
            ),
            shape = shape,
        )
        RingProgressIndicator(
            progress = RingProgress.Segmented(
                segments = 10,
                fraction = { 0.75f },
            ),
            shape = shape,
            strokeCap = StrokeCap.Butt,
        )
        RingProgressIndicator(
            progress = RingProgress.Determinate(fraction = { 0.6f }),
            shape = shape,
        )
        RingProgressIndicator(
            progress = RingProgress.Indeterminate,
            shape = shape,
        )
    }
}
