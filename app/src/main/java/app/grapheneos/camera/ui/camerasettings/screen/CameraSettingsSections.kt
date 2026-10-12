package app.grapheneos.camera.ui.camerasettings.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.CameraFront
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.ShutterSpeed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.VideoStable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.camerasettings.components.SettingsCategoryHeader
import app.grapheneos.camera.ui.camerasettings.components.SettingsClickableItem
import app.grapheneos.camera.ui.camerasettings.components.SettingsSwitchItem
import app.grapheneos.camera.ui.camerasettings.components.formatMegapixels
import app.grapheneos.camera.ui.camerasettings.components.formatPhotoQuality
import app.grapheneos.camera.ui.camerasettings.components.groupedItemShape
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsAction as Action
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsUiState as UiState
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoQualityUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoResolutionUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.SwitchSettingUiModel
import app.grapheneos.camera.ui.components.motion.SETTLE_SIZE_SPEC
import app.grapheneos.camera.util.storageLocationToUiString

private val FOOTER_HORIZONTAL_PADDING = 8.dp
private val FOOTER_TOP_PADDING = 16.dp

internal fun LazyListScope.cameraSettingsSections(
    uiState: UiState,
    onAction: (Action) -> Unit,
) {
    settingsSection(
        key = "privacy",
        title = R.string.camera_settings_section_privacy,
        rows = privacyRows(uiState, onAction),
    )
    settingsSection(
        key = "composition",
        title = R.string.camera_settings_section_composition,
        rows = compositionRows(uiState, onAction),
    )
    settingsSection(
        key = "quality",
        title = R.string.camera_settings_section_quality,
        rows = qualityRows(uiState, onAction),
    )
    settingsSection(
        key = "sound",
        title = R.string.camera_settings_section_sound,
        rows = soundRows(uiState, onAction),
    )
    settingsSection(
        key = "controls",
        title = R.string.camera_settings_section_controls,
        rows = controlsRows(uiState, onAction),
    )
    settingsSection(
        key = "storage",
        title = R.string.storage,
        rows = storageRows(uiState, onAction),
    )

    if (uiState.canResetSettings) {
        item(key = "reset_settings") {
            ResetSettingsFooter(
                onClick = { onAction(Action.ResetSettingsClicked) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

private fun LazyListScope.settingsSection(
    key: String,
    title: Int,
    rows: List<SettingsRow>,
) {
    if (rows.isEmpty()) return

    item(key = "${key}_header") {
        SettingsCategoryHeader(title = stringResource(title))
    }

    rows.forEachIndexed { index, row ->
        item(key = row.key) {
            row.content(
                groupedItemShape(
                    index = index,
                    count = rows.size,
                ),
            )
        }
    }
}

private fun privacyRows(
    uiState: UiState,
    onAction: (Action) -> Unit,
): List<SettingsRow> {
    return listOf(
        SettingsRow(key = "geo_tagging") { shape ->
            val summary = when {
                uiState.geoTagging.enabled -> R.string.camera_settings_geo_tagging_summary
                else -> R.string.camera_settings_geo_tagging_locked
            }

            SwitchSettingItem(
                setting = uiState.geoTagging,
                title = stringResource(R.string.camera_settings_geo_tagging),
                summary = stringResource(summary),
                icon = Icons.Outlined.LocationOn,
                shape = shape,
                onCheckedChange = { onAction(Action.GeoTaggingToggled(it)) },
            )
        },
        SettingsRow(key = "remove_exif") { shape ->
            val summary = when {
                uiState.removeExif.enabled -> R.string.remove_exif_subtitle
                else -> R.string.image_taken_in_this_mode_does_not_contain_extra_data
            }

            SwitchSettingItem(
                setting = uiState.removeExif,
                title = stringResource(R.string.remove_exif_title),
                summary = stringResource(summary),
                label = stringResource(R.string.camera_settings_photo_only),
                icon = Icons.Outlined.PrivacyTip,
                shape = shape,
                onCheckedChange = { onAction(Action.RemoveExifToggled(it)) },
            )
        },
    )
}

private fun compositionRows(
    uiState: UiState,
    onAction: (Action) -> Unit,
): List<SettingsRow> {
    return listOf(
        SettingsRow(key = "grid") { shape ->
            SettingsClickableItem(
                title = stringResource(R.string.camera_settings_grid),
                summary = stringResource(uiState.grid.selected.label),
                icon = Icons.Outlined.GridOn,
                isChanged = uiState.grid.isChanged,
                shape = shape,
                onClick = { onAction(Action.GridClicked) },
            )
        },
        SettingsRow(key = "gyroscope_suggestions") { shape ->
            SwitchSettingItem(
                setting = uiState.gyroscopeSuggestions,
                title = stringResource(R.string.gyroscope_suggestions),
                summary = stringResource(R.string.gyroscope_setting_desc),
                label = stringResource(R.string.camera_settings_photo_only),
                icon = Icons.Outlined.Straighten,
                shape = shape,
                onCheckedChange = { onAction(Action.GyroscopeSuggestionsToggled(it)) },
            )
        },
        SettingsRow(key = "keep_selfies_as_seen") { shape ->
            SwitchSettingItem(
                setting = uiState.keepSelfiesAsSeen,
                title = stringResource(R.string.camera_settings_keep_selfies_as_seen),
                summary = stringResource(R.string.camera_settings_keep_selfies_as_seen_summary),
                icon = Icons.Outlined.CameraFront,
                shape = shape,
                onCheckedChange = { onAction(Action.KeepSelfiesAsSeenToggled(it)) },
            )
        },
    )
}

private fun qualityRows(
    uiState: UiState,
    onAction: (Action) -> Unit,
): List<SettingsRow> {
    return listOf(
        SettingsRow(key = "photo_quality") { shape ->
            SettingsClickableItem(
                title = stringResource(R.string.image_quality),
                summary = photoQualitySummary(uiState.photoQuality),
                label = stringResource(R.string.camera_settings_photo_only),
                icon = Icons.Outlined.HighQuality,
                isChanged = uiState.photoQuality.isChanged,
                shape = shape,
                onClick = { onAction(Action.PhotoQualityClicked) },
            )
        },
        SettingsRow(key = "capture_mode") { shape ->
            SettingsClickableItem(
                title = stringResource(R.string.camera_settings_capture_mode),
                summary = stringResource(uiState.captureMode.selected.label),
                label = stringResource(R.string.camera_settings_photo_only),
                icon = Icons.Outlined.ShutterSpeed,
                isChanged = uiState.captureMode.isChanged,
                shape = shape,
                onClick = { onAction(Action.CaptureModeClicked) },
            )
        },
        SettingsRow(key = "video_stabilization") { shape ->
            val summary = when {
                uiState.videoStabilization.enabled -> {
                    R.string.camera_settings_video_stabilization_summary
                }

                else -> R.string.camera_settings_video_stabilization_unavailable
            }

            SwitchSettingItem(
                setting = uiState.videoStabilization,
                title = stringResource(R.string.camera_settings_video_stabilization),
                summary = stringResource(summary),
                label = stringResource(R.string.camera_settings_video_only),
                icon = Icons.Outlined.VideoStable,
                shape = shape,
                onCheckedChange = { onAction(Action.VideoStabilizationToggled(it)) },
            )
        },
    )
}

private fun soundRows(
    uiState: UiState,
    onAction: (Action) -> Unit,
): List<SettingsRow> {
    return listOf(
        SettingsRow(key = "camera_sounds") { shape ->
            SwitchSettingItem(
                setting = uiState.cameraSounds,
                title = stringResource(R.string.camera_sounds),
                summary = stringResource(R.string.camera_sounds_desc),
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                shape = shape,
                onCheckedChange = { onAction(Action.CameraSoundsToggled(it)) },
            )
        },
    )
}

private fun controlsRows(
    uiState: UiState,
    onAction: (Action) -> Unit,
): List<SettingsRow> {
    return listOf(
        SettingsRow(key = "focus_timeout") { shape ->
            SettingsClickableItem(
                title = stringResource(R.string.camera_settings_focus_timeout),
                summary = focusTimeoutLabel(uiState.focusTimeout.selected),
                icon = Icons.Outlined.CenterFocusWeak,
                isChanged = uiState.focusTimeout.isChanged,
                shape = shape,
                onClick = { onAction(Action.FocusTimeoutClicked) },
            )
        },
    )
}

private fun storageRows(
    uiState: UiState,
    onAction: (Action) -> Unit,
): List<SettingsRow> {
    val storageLocation = uiState.storageLocation ?: return emptyList()

    return listOf(
        SettingsRow(key = "storage_location") { shape ->
            val context = LocalContext.current

            SettingsClickableItem(
                title = stringResource(R.string.storage_location),
                summary = remember(storageLocation.location) {
                    storageLocationToUiString(context, storageLocation.location)
                },
                icon = Icons.Outlined.Folder,
                isChanged = storageLocation.isChanged,
                shape = shape,
                onClick = { onAction(Action.StorageLocationClicked) },
                modifier = Modifier.animateContentSize(animationSpec = SETTLE_SIZE_SPEC),
                trailing = when {
                    storageLocation.isChanged -> {
                        { ResetStorageLocationButton(onAction = onAction) }
                    }

                    else -> null
                },
            )
        },
    )
}

@Composable
private fun ResetStorageLocationButton(onAction: (Action) -> Unit) {
    val description = stringResource(R.string.camera_settings_reset_storage_location)

    OutlinedButton(
        onClick = { onAction(Action.StorageLocationResetClicked) },
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(text = stringResource(R.string.camera_settings_reset))
    }
}

@Composable
private fun ResetSettingsFooter(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val link = stringResource(R.string.camera_settings_reset)
    val sentence = stringResource(R.string.camera_settings_reset_settings_footer, link)
    val linkStart = sentence.indexOf(link)
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        ),
    )
    val text = buildAnnotatedString {
        append(sentence)
        addLink(
            clickable = LinkAnnotation.Clickable(
                tag = link,
                styles = linkStyles,
            ) { onClick() },
            start = linkStart,
            end = linkStart + link.length,
        )
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = FOOTER_HORIZONTAL_PADDING,
                end = FOOTER_HORIZONTAL_PADDING,
                top = FOOTER_TOP_PADDING,
            ),
    )
}

@Composable
private fun SwitchSettingItem(
    setting: SwitchSettingUiModel,
    title: String,
    summary: String,
    icon: ImageVector,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit,
    label: String? = null,
) {
    SettingsSwitchItem(
        title = title,
        checked = setting.checked,
        onCheckedChange = onCheckedChange,
        summary = summary,
        label = label,
        icon = icon,
        enabled = setting.enabled,
        isChanged = setting.isChanged,
        shape = shape,
    )
}

@Composable
private fun photoQualitySummary(photoQuality: PhotoQualityUiModel): String {
    val percent = formatPhotoQuality(photoQuality.quality)
    val compression = stringResource(photoQuality.level.label)
    val resolution = photoQuality.resolution
    val resolutionLabel = when {
        resolution != null -> megapixels(
            resolution = resolution,
            isFull = photoQuality.isFullResolution,
        )

        photoQuality.isFullResolution -> stringResource(
            R.string.camera_settings_photo_quality_full_resolution,
        )

        else -> null
    }

    return when (resolutionLabel) {
        null -> stringResource(
            R.string.camera_settings_photo_quality_summary,
            compression,
            percent,
        )

        else -> stringResource(
            R.string.camera_settings_photo_quality_summary_with_resolution,
            resolutionLabel,
            compression,
            percent,
        )
    }
}

@Composable
private fun megapixels(
    resolution: PhotoResolutionUiModel,
    isFull: Boolean,
): String {
    val megapixels = when {
        isFull -> resolution.fullMegapixels
        else -> resolution.standardMegapixels
    }

    return formatMegapixels(megapixels)
}

@Composable
internal fun focusTimeoutLabel(seconds: Long): String {
    return when (seconds) {
        0L -> stringResource(R.string.camera_settings_focus_timeout_off)
        else -> pluralStringResource(
            R.plurals.camera_settings_seconds,
            seconds.toInt(),
            seconds,
        )
    }
}

private class SettingsRow(
    val key: String,
    val content: @Composable (shape: Shape) -> Unit,
)
