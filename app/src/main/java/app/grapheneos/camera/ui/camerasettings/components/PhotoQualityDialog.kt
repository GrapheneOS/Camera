package app.grapheneos.camera.ui.camerasettings.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.components.motion.SETTLE_COLOR_SPEC
import app.grapheneos.camera.ui.core.CameraPreviewTheme
import java.text.NumberFormat
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

private const val TILES_PER_ROW = 2
private const val PERCENT = 100.0

private val DIALOG_PADDING = 24.dp
private val TILE_SPACING = 8.dp

@Composable
internal fun PhotoQualityDialog(
    quality: Int,
    options: ImmutableList<PhotoQualityOption>,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            PhotoQualityDialogContent(
                quality = quality,
                options = options,
                onConfirm = onConfirm,
            )
        }
    }
}

@Composable
private fun PhotoQualityDialogContent(
    quality: Int,
    options: ImmutableList<PhotoQualityOption>,
    onConfirm: (Int) -> Unit,
) {
    var selectedQuality by rememberSaveable { mutableIntStateOf(quality) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(DIALOG_PADDING),
    ) {
        Text(
            text = stringResource(R.string.image_quality),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )

        Spacer(modifier = Modifier.height(16.dp))

        PhotoQualityTiles(
            options = options,
            selectedQuality = selectedQuality,
            onSelect = { selectedQuality = it },
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onConfirm(selectedQuality) },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.End),
        ) {
            Text(text = stringResource(R.string.done))
        }
    }
}

@Composable
private fun PhotoQualityTiles(
    options: ImmutableList<PhotoQualityOption>,
    selectedQuality: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(TILE_SPACING),
    ) {
        options.chunked(TILES_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
                row.forEach { option ->
                    PhotoQualityTile(
                        option = option,
                        selected = option.quality == selectedQuality,
                        onClick = { onSelect(option.quality) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoQualityTile(
    option: PhotoQualityOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> colorScheme.primaryContainer
            else -> colorScheme.surfaceContainerHighest
        },
        animationSpec = SETTLE_COLOR_SPEC,
        label = "photo_quality_tile_container",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            selected -> colorScheme.onPrimaryContainer
            else -> colorScheme.onSurface
        },
        animationSpec = SETTLE_COLOR_SPEC,
        label = "photo_quality_tile_content",
    )

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(containerColor)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
        )
        Text(
            text = formatPhotoQuality(option.quality),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
        )
    }
}

@Immutable
internal data class PhotoQualityOption(
    val quality: Int,
    val label: String,
)

internal fun formatPhotoQuality(quality: Int): String {
    return NumberFormat.getPercentInstance().format(quality / PERCENT)
}

@PreviewLightDark
@Composable
private fun PhotoQualityDialogLevelPreview() {
    PhotoQualityDialogPreview(quality = 95)
}

@PreviewLightDark
@Composable
private fun PhotoQualityDialogCustomPreview() {
    PhotoQualityDialogPreview(quality = 77)
}

@Composable
private fun PhotoQualityDialogPreview(quality: Int) {
    CameraPreviewTheme {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            PhotoQualityDialogContent(
                quality = quality,
                options = persistentListOf(
                    PhotoQualityOption(quality = 100, label = "Maximum"),
                    PhotoQualityOption(quality = 95, label = "High"),
                    PhotoQualityOption(quality = 90, label = "Standard"),
                    PhotoQualityOption(quality = 75, label = "Compact"),
                ),
                onConfirm = {},
            )
        }
    }
}
