package app.grapheneos.camera.ui.camerasettings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import java.text.NumberFormat
import kotlin.math.roundToInt
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
    presets: ImmutableList<PhotoQualityOption>,
    custom: PhotoQualityOption,
    customRange: IntRange,
    resolutions: PhotoResolutionOptions?,
    onConfirm: (quality: Int, isFullResolution: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val isPreset = presets.any { it.quality == quality }
    var isCustomSelected by rememberSaveable { mutableStateOf(!isPreset) }
    var presetQuality by rememberSaveable { mutableIntStateOf(quality) }
    var customQuality by rememberSaveable {
        mutableIntStateOf(
            when {
                isPreset -> custom.quality
                else -> quality
            },
        )
    }
    var selectedFullResolution by rememberSaveable { mutableStateOf(isFullResolution) }

    SettingsDialogFrame(
        title = stringResource(R.string.image_quality),
        onConfirm = {
            val selectedQuality = when {
                isCustomSelected -> customQuality
                else -> presetQuality
            }
            onConfirm(selectedQuality, selectedFullResolution)
        },
        onDismiss = onDismiss,
    ) {
        PhotoQualityDialogContent(
            selection = PhotoQualitySelection(
                presetQuality = presetQuality,
                customQuality = customQuality,
                isCustomSelected = isCustomSelected,
            ),
            onSelectPreset = { preset ->
                presetQuality = preset
                isCustomSelected = false
            },
            onSelectCustom = { isCustomSelected = true },
            onCustomQualityChange = { customQuality = it },
            isFullResolution = selectedFullResolution,
            onSelectResolution = { selectedFullResolution = it },
            presets = presets,
            custom = custom,
            customRange = customRange,
            resolutions = resolutions,
        )
    }
}

@Composable
private fun PhotoQualityDialogContent(
    selection: PhotoQualitySelection,
    onSelectPreset: (Int) -> Unit,
    onSelectCustom: () -> Unit,
    onCustomQualityChange: (Int) -> Unit,
    isFullResolution: Boolean,
    onSelectResolution: (Boolean) -> Unit,
    presets: ImmutableList<PhotoQualityOption>,
    custom: PhotoQualityOption,
    customRange: IntRange,
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
                SettingsTile(
                    title = formatMegapixels(resolutions.standardMegapixels),
                    subtitle = stringResource(R.string.camera_settings_resolution_default),
                    selected = !isFullResolution,
                    onClick = { onSelectResolution(false) },
                    modifier = Modifier.weight(1f),
                )
                SettingsTile(
                    title = formatMegapixels(resolutions.fullMegapixels),
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
            selection = selection,
            onSelectPreset = onSelectPreset,
            onSelectCustom = onSelectCustom,
            onCustomQualityChange = onCustomQualityChange,
            presets = presets,
            custom = custom,
            customRange = customRange,
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
    selection: PhotoQualitySelection,
    onSelectPreset: (Int) -> Unit,
    onSelectCustom: () -> Unit,
    onCustomQualityChange: (Int) -> Unit,
    presets: ImmutableList<PhotoQualityOption>,
    custom: PhotoQualityOption,
    customRange: IntRange,
) {
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
        verticalArrangement = Arrangement.spacedBy(TILE_SPACING),
        maxItemsInEachRow = TILES_PER_ROW,
    ) {
        presets.forEach { preset ->
            SettingsTile(
                title = preset.label,
                subtitle = formatPhotoQuality(preset.quality),
                selected = !selection.isCustomSelected && preset.quality == selection.presetQuality,
                onClick = { onSelectPreset(preset.quality) },
                modifier = Modifier.weight(1f),
            )
        }
        SettingsSliderTile(
            title = custom.label,
            subtitle = formatPhotoQuality(selection.customQuality),
            selected = selection.isCustomSelected,
            value = selection.customQuality.toFloat(),
            onValueChange = { onCustomQualityChange(it.roundToInt()) },
            onClick = onSelectCustom,
            valueRange = customRange.first.toFloat()..customRange.last.toFloat(),
            steps = customRange.last - customRange.first - 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Immutable
private data class PhotoQualitySelection(
    val presetQuality: Int,
    val customQuality: Int,
    val isCustomSelected: Boolean,
)

@Immutable
internal data class PhotoQualityOption(
    val quality: Int,
    val label: String,
)

@Immutable
internal data class PhotoResolutionOptions(
    val standardMegapixels: Int,
    val fullMegapixels: Int,
)

internal fun formatPhotoQuality(quality: Int): String {
    return NumberFormat.getPercentInstance().format(quality / PERCENT)
}

@Composable
internal fun formatMegapixels(megapixels: Int): String {
    return stringResource(R.string.camera_settings_megapixels, megapixels)
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
            standardMegapixels = 12,
            fullMegapixels = 50,
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
                    selection = PhotoQualitySelection(
                        presetQuality = 90,
                        customQuality = 75,
                        isCustomSelected = true,
                    ),
                    onSelectPreset = {},
                    onSelectCustom = {},
                    onCustomQualityChange = {},
                    isFullResolution = false,
                    onSelectResolution = {},
                    presets = persistentListOf(
                        PhotoQualityOption(quality = 100, label = "Maximum"),
                        PhotoQualityOption(quality = 95, label = "High"),
                        PhotoQualityOption(quality = 90, label = "Standard"),
                    ),
                    custom = PhotoQualityOption(quality = 75, label = "Custom"),
                    customRange = 1..100,
                    resolutions = resolutions,
                )
            }
        }
    }
}
