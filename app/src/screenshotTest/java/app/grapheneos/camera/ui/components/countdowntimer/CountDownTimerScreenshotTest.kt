package app.grapheneos.camera.ui.components.countdowntimer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.cameraColors
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PreviewLightDark
@Composable
private fun CountDownTimerValues() {
    CameraPreviewColumn {
        CountDownTimerFlow()
    }
}

@PreviewTest
@Preview(
    name = "Arabic",
    locale = "ar-rEG",
)
@Preview(
    name = "Persian",
    locale = "fa",
)
@Preview(
    name = "Marathi",
    locale = "mr",
)
@Composable
private fun CountDownTimerLayouts() {
    CameraPreviewColumn {
        CountDownTimerFlow()
    }
}

@Composable
private fun CountDownTimerFlow() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
        verticalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        listOf(3, 10).forEach { value ->
            CountDownTimer(
                value = value,
                modifier = Modifier.overScrim(),
            )
        }
        CountDownTimer(
            value = 10,
            modifier = Modifier
                .size(size = 96.dp)
                .overScrim(),
        )
    }
}

@Composable
private fun Modifier.overScrim(): Modifier {
    return background(
        color = MaterialTheme.cameraColors.overlayScrim,
        shape = MaterialTheme.shapes.large,
    )
}
