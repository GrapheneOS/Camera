package app.grapheneos.camera.ui.components.valuechip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PreviewLightDark
@Composable
private fun ValueChipTexts() {
    CameraPreviewColumn {
        ValueChipRow()
    }
}

@PreviewTest
@Preview(
    name = "LargeFont",
    fontScale = 2f,
)
@Composable
private fun ValueChipLayouts() {
    CameraPreviewColumn {
        ValueChipRow()
    }
}

@Composable
private fun ValueChipRow() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        ValueChip(text = "0")
        ValueChip(text = "Auto")
        ValueChip(text = "3968K")
        ValueChip(text = "2.2×")
    }
}
