package app.grapheneos.camera.ui.components.choicebutton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.preview.CameraPreviewColumn
import app.grapheneos.camera.ui.core.preview.PREVIEW_MIC_ICON
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PreviewLightDark
@Composable
private fun ChoiceButtonIcons() {
    CameraPreviewColumn {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            listOf(true, false).forEach { enabled ->
                listOf(true, false).forEach { selected ->
                    ChoiceButton(
                        selected = selected,
                        onClick = {},
                        enabled = enabled,
                    ) {
                        Icon(
                            imageVector = PREVIEW_MIC_ICON,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

@PreviewTest
@PreviewLightDark
@Composable
private fun ChoiceButtonLabels() {
    CameraPreviewColumn {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            listOf("0.5×", "1×", "2×").forEach { label ->
                ChoiceButton(
                    text = label,
                    selected = label == "1×",
                    onClick = {},
                )
            }
        }
    }
}
