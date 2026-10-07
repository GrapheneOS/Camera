package app.grapheneos.camera.ui.components.adjustmentbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import app.grapheneos.camera.ui.core.PREVIEW_BRIGHTNESS_HIGH_ICON
import app.grapheneos.camera.ui.core.PREVIEW_BRIGHTNESS_LOW_ICON
import app.grapheneos.camera.ui.core.PREVIEW_COOL_TINT
import app.grapheneos.camera.ui.core.PREVIEW_WARM_TINT
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PreviewLightDark
@Composable
private fun AdjustmentBarValues() {
    CameraPreviewColumn {
        AdjustmentBarColumn()
    }
}

@PreviewTest
@Preview(
    name = "Arabic",
    locale = "ar-rEG",
)
@Composable
private fun AdjustmentBarLayouts() {
    CameraPreviewColumn {
        AdjustmentBarColumn()
    }
}

@PreviewTest
@PreviewLightDark
@Composable
private fun AdjustmentBarTints() {
    CameraPreviewColumn {
        val colors = AdjustmentBarColors.fromTheme().copy(
            startTint = PREVIEW_COOL_TINT,
            endTint = PREVIEW_WARM_TINT,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            listOf(-12f, -6f, 6f, 12f).forEach { value ->
                ExposureBar(
                    value = value,
                    colors = colors,
                )
            }
        }
    }
}

@Composable
private fun AdjustmentBarColumn() {
    Column(
        verticalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        listOf(-12f, 0f, 7f, 12f).forEach { value ->
            ExposureBar(value = value)
        }
        ExposureBar(
            value = 2f,
            hasIcons = false,
        )
        ExposureBar(
            value = 0f,
            enabled = false,
        )
    }
}

@Composable
private fun ExposureBar(
    value: Float,
    hasIcons: Boolean = true,
    enabled: Boolean = true,
    colors: AdjustmentBarColors = AdjustmentBarColors.fromTheme(),
) {
    AdjustmentBar(
        value = value,
        onValueChange = {},
        valueRange = -12f..12f,
        steps = 23,
        enabled = enabled,
        startIcon = PREVIEW_BRIGHTNESS_LOW_ICON.takeIf { hasIcons },
        endIcon = PREVIEW_BRIGHTNESS_HIGH_ICON.takeIf { hasIcons },
        colors = colors,
    )
}
