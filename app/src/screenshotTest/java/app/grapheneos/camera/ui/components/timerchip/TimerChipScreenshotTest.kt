package app.grapheneos.camera.ui.components.timerchip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@PreviewTest
@PreviewLightDark
@Composable
private fun TimerChipDurations() {
    CameraPreviewColumn {
        TimerChipColumn()
    }
}

@PreviewTest
@Preview(
    name = "Arabic",
    locale = "ar",
)
@Preview(
    name = "Persian",
    locale = "fa",
)
@Preview(
    name = "LargeFont",
    fontScale = 2f,
)
@Composable
private fun TimerChipLocales() {
    CameraPreviewColumn {
        TimerChipColumn()
    }
}

@Composable
private fun TimerChipColumn() {
    Column(
        verticalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        TimerChip(elapsed = 1.seconds)
        TimerChip(
            elapsed = 1.seconds,
            label = "PAUSED",
        )
        TimerChip(elapsed = 1.hours + 2.minutes + 3.seconds)
    }
}
