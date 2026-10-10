package app.grapheneos.camera.ui.camerasettings.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.components.motion.SETTLE_COLOR_SPEC
import app.grapheneos.camera.ui.core.CameraPreviewTheme
import java.text.NumberFormat
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

private const val TILES_PER_ROW = 2
private const val PERCENT = 100.0

private val TILE_SPACING = 8.dp
private val SECTION_SPACING = 16.dp

@Composable
internal fun PhotoQualityDialog(
    quality: Int,
    isFullResolution: Boolean,
    options: ImmutableList<PhotoQualityOption>,
    resolutions: PhotoResolutionOptions?,
    onConfirm: (quality: Int, isFullResolution: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedQuality by rememberSaveable { mutableIntStateOf(quality) }
    var selectedFullResolution by rememberSaveable { mutableStateOf(isFullResolution) }

    SettingsDialogFrame(
        title = stringResource(R.string.image_quality),
        onConfirm = { onConfirm(selectedQuality, selectedFullResolution) },
        onDismiss = onDismiss,
    ) {
        PhotoQualityDialogContent(
            selectedQuality = selectedQuality,
            onSelectQuality = { selectedQuality = it },
            isFullResolution = selectedFullResolution,
            onSelectResolution = { selectedFullResolution = it },
            options = options,
            resolutions = resolutions,
        )
    }
}

@Composable
private fun PhotoQualityDialogContent(
    selectedQuality: Int,
    onSelectQuality: (Int) -> Unit,
    isFullResolution: Boolean,
    onSelectResolution: (Boolean) -> Unit,
    options: ImmutableList<PhotoQualityOption>,
    resolutions: PhotoResolutionOptions?,
) {
    Column(
        modifier = Modifier.padding(horizontal = DIALOG_PADDING),
        verticalArrangement = Arrangement.spacedBy(TILE_SPACING),
    ) {
        if (resolutions != null) {
            PhotoQualitySectionLabel(text = stringResource(R.string.camera_settings_resolution))
            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
            ) {
                PhotoQualityTile(
                    title = resolutions.standard,
                    subtitle = stringResource(R.string.camera_settings_resolution_default),
                    selected = !isFullResolution,
                    onClick = { onSelectResolution(false) },
                    modifier = Modifier.weight(1f),
                )
                PhotoQualityTile(
                    title = resolutions.full,
                    subtitle = stringResource(R.string.camera_settings_resolution_full),
                    selected = isFullResolution,
                    onClick = { onSelectResolution(true) },
                    modifier = Modifier.weight(1f),
                )
            }
            PhotoQualitySectionLabel(
                text = stringResource(R.string.camera_settings_compression),
                modifier = Modifier.padding(top = SECTION_SPACING - TILE_SPACING),
            )
        }

        PhotoQualityTiles(
            options = options,
            selectedQuality = selectedQuality,
            onSelect = onSelectQuality,
        )
    }
}

@Composable
private fun PhotoQualitySectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
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
                        title = option.label,
                        subtitle = formatPhotoQuality(option.quality),
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
    title: String,
    subtitle: String,
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
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
        )
        Text(
            text = subtitle,
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

@Immutable
internal data class PhotoResolutionOptions(
    val standard: String,
    val full: String,
)

internal fun formatPhotoQuality(quality: Int): String {
    return NumberFormat.getPercentInstance().format(quality / PERCENT)
}

@PreviewLightDark
@Composable
private fun PhotoQualityDialogPreview() {
    PhotoQualityDialogPreviewContent(resolutions = null)
}

@PreviewLightDark
@Composable
private fun PhotoQualityDialogResolutionPreview() {
    PhotoQualityDialogPreviewContent(
        resolutions = PhotoResolutionOptions(
            standard = "12 MP",
            full = "50 MP",
        ),
    )
}

@Composable
private fun PhotoQualityDialogPreviewContent(resolutions: PhotoResolutionOptions?) {
    CameraPreviewTheme {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            SettingsDialogFrameContent(
                title = "Image quality",
                onConfirm = {},
            ) {
                PhotoQualityDialogContent(
                    selectedQuality = 95,
                    onSelectQuality = {},
                    isFullResolution = false,
                    onSelectResolution = {},
                    options = persistentListOf(
                        PhotoQualityOption(quality = 100, label = "Maximum"),
                        PhotoQualityOption(quality = 95, label = "High"),
                        PhotoQualityOption(quality = 90, label = "Standard"),
                        PhotoQualityOption(quality = 75, label = "Compact"),
                    ),
                    resolutions = resolutions,
                )
            }
        }
    }
}
