package app.grapheneos.camera.ui.components.pillselector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.pillselector.model.PillSelectorSpacing
import app.grapheneos.camera.ui.core.CameraPreviewColumn
import com.android.tools.screenshot.PreviewTest

private val TEXT_LABELS = listOf(
    "Portrait",
    "Photo",
    "Night Sight",
)
private val LETTER_LABELS = listOf(
    "A",
    "B",
    "C",
)

@PreviewTest
@PreviewLightDark
@Composable
private fun PillSelectorTexts() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            TEXT_LABELS.indices.forEach { selectedIndex ->
                TextPillSelector(selectedIndex = selectedIndex)
            }
        }
    }
}

@PreviewTest
@PreviewLightDark
@Composable
private fun PillSelectorStates() {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            LetterPillSelector(spacing = PillSelectorSpacing.Spaced)
            LetterPillSelector(spacing = PillSelectorSpacing.Joined)
            LetterPillSelector(
                spacing = PillSelectorSpacing.Spaced,
                colors = PillSelectorColors.fromSurfaceTheme(),
            )
            LetterPillSelector(
                spacing = PillSelectorSpacing.Spaced,
                enabled = false,
            )
        }
    }
}

@Composable
private fun TextPillSelector(selectedIndex: Int) {
    PillSelector(
        selectedIndex = selectedIndex,
        itemCount = TEXT_LABELS.size,
        onItemSelected = {},
    ) { index ->
        BasicText(
            text = TEXT_LABELS[index],
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}

@Composable
private fun LetterPillSelector(
    spacing: PillSelectorSpacing,
    enabled: Boolean = true,
    colors: PillSelectorColors = PillSelectorColors.fromTheme(),
) {
    PillSelector(
        selectedIndex = 1,
        itemCount = LETTER_LABELS.size,
        onItemSelected = {},
        enabled = enabled,
        spacing = spacing,
        colors = colors,
    ) { index ->
        Box(
            modifier = Modifier.size(size = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = LETTER_LABELS[index],
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}
