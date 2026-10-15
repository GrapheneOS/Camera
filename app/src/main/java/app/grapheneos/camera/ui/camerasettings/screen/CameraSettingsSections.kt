package app.grapheneos.camera.ui.camerasettings.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.CameraFront
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.HdrOn
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.ShutterSpeed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VideoStable
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.camerasettings.components.SettingsCategoryHeader
import app.grapheneos.camera.ui.camerasettings.components.SettingsClickableItem
import app.grapheneos.camera.ui.camerasettings.components.SettingsStorageCard
import app.grapheneos.camera.ui.camerasettings.components.SettingsSwitchItem
import app.grapheneos.camera.ui.camerasettings.components.groupedItemShape
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsAction as Action
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsUiState as UiState
import app.grapheneos.camera.ui.camerasettings.screen.model.SwitchSettingUiModel
import app.grapheneos.camera.ui.components.motion.SETTLE_SIZE_SPEC
import app.grapheneos.camera.util.storageLocationToUiString

private val FOOTER_TOP_PADDING = 4.dp

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
    settingsSection(
        key = "about",
        title = R.string.camera_settings_section_about,
        rows = aboutRows(uiState, onAction),
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
        SettingsCategoryHeader(
            title = stringResource(title),
            modifier = Modifier.animateItem(),
        )
    }

    rows.forEachIndexed { index, row ->
        item(key = row.key) {
            row.content(
                groupedItemShape(
                    index = index,
                    count = rows.size,
                ),
                Modifier.animateItem(),
            )
        }
    }
}

private fun privacyRows(
    uiState: UiState,
    onAction: (Action) -> Unit,
): List<SettingsRow> {
    return listOf(
        SettingsRow(key = "geo_tagging") { shape, modifier ->
            val summary = when {
                uiState.offersLocationSettings -> R.string.camera_settings_geo_tagging_access_off
                uiState.geoTagging.enabled -> R.string.camera_settings_geo_tagging_summary
                else -> R.string.camera_settings_geo_tagging_locked
            }
            SwitchSettingItem(
                setting = uiState.geoTagging,
                title = stringResource(R.string.camera_settings_geo_tagging),
                summary = stringResource(summary),
                label = stringResource(R.string.camera_settings_open_settings)
                    .takeIf { uiState.offersLocationSettings },
                onLabelClick = { onAction(Action.LocationSettingsClicked) },
                icon = Icons.Outlined.LocationOn,
                shape = shape,
                modifier = modifier,
                onCheckedChange = { onAction(Action.GeoTaggingToggled(it)) },
            )
        },
        SettingsRow(key = "remove_exif") { shape, modifier ->
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
                modifier = modifier,
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
        SettingsRow(key = "grid") { shape, modifier ->
            SettingsClickableItem(
                title = stringResource(R.string.camera_settings_grid),
                summary = stringResource(uiState.grid.selected.label),
                icon = Icons.Outlined.GridOn,
                isChanged = uiState.grid.isChanged,
                shape = shape,
                modifier = modifier,
                onClick = { onAction(Action.GridClicked) },
            )
        },
        SettingsRow(key = "gyroscope_suggestions") { shape, modifier ->
            SwitchSettingItem(
                setting = uiState.gyroscopeSuggestions,
                title = stringResource(R.string.gyroscope_suggestions),
                summary = stringResource(R.string.gyroscope_setting_desc),
                label = stringResource(R.string.camera_settings_photo_only),
                icon = Icons.Outlined.Straighten,
                shape = shape,
                modifier = modifier,
                onCheckedChange = { onAction(Action.GyroscopeSuggestionsToggled(it)) },
            )
        },
        SettingsRow(key = "keep_selfies_as_seen") { shape, modifier ->
            SwitchSettingItem(
                setting = uiState.keepSelfiesAsSeen,
                title = stringResource(R.string.camera_settings_keep_selfies_as_seen),
                summary = stringResource(R.string.camera_settings_keep_selfies_as_seen_summary),
                icon = Icons.Outlined.CameraFront,
                shape = shape,
                modifier = modifier,
                onCheckedChange = { onAction(Action.KeepSelfiesAsSeenToggled(it)) },
            )
        },
    )
}

private fun qualityRows(
    uiState: UiState,
    onAction: (Action) -> Unit,
): List<SettingsRow> {
    return listOfNotNull(
        SettingsRow(key = "photo_quality") { shape, modifier ->
            SettingsClickableItem(
                title = stringResource(R.string.image_quality),
                summary = photoQualitySummary(uiState.photoQuality),
                label = stringResource(R.string.camera_settings_photo_only),
                icon = Icons.Outlined.HighQuality,
                isChanged = uiState.photoQuality.isChanged,
                shape = shape,
                modifier = modifier,
                onClick = { onAction(Action.PhotoQualityClicked) },
            )
        },
        SettingsRow(key = "capture_mode") { shape, modifier ->
            SettingsClickableItem(
                title = stringResource(R.string.camera_settings_capture_mode),
                summary = stringResource(uiState.captureMode.selected.label),
                label = stringResource(R.string.camera_settings_photo_only),
                icon = Icons.Outlined.ShutterSpeed,
                isChanged = uiState.captureMode.isChanged,
                shape = shape,
                modifier = modifier,
                onClick = { onAction(Action.CaptureModeClicked) },
            )
        },
        SettingsRow(key = "video_stabilization") { shape, modifier ->
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
                modifier = modifier,
                onCheckedChange = { onAction(Action.VideoStabilizationToggled(it)) },
            )
        },
        uiState.hdrVideo?.let { hdrVideo ->
            SettingsRow(key = "hdr_video") { shape, modifier ->
                SwitchSettingItem(
                    setting = hdrVideo,
                    title = stringResource(R.string.camera_settings_hdr_video),
                    summary = stringResource(R.string.camera_settings_hdr_video_summary),
                    label = stringResource(R.string.camera_settings_video_only),
                    icon = Icons.Outlined.HdrOn,
                    shape = shape,
                    modifier = modifier,
                    onCheckedChange = { onAction(Action.HdrVideoToggled(it)) },
                )
            }
        },
    )
}

private fun soundRows(
    uiState: UiState,
    onAction: (Action) -> Unit,
): List<SettingsRow> {
    return listOf(
        SettingsRow(key = "camera_sounds") { shape, modifier ->
            SwitchSettingItem(
                setting = uiState.cameraSounds,
                title = stringResource(R.string.camera_sounds),
                summary = stringResource(R.string.camera_sounds_desc),
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                shape = shape,
                modifier = modifier,
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
        SettingsRow(key = "focus_timeout") { shape, modifier ->
            SettingsClickableItem(
                title = stringResource(R.string.camera_settings_focus_timeout),
                summary = focusTimeoutLabel(uiState.focusTimeout.selected),
                icon = Icons.Outlined.CenterFocusWeak,
                isChanged = uiState.focusTimeout.isChanged,
                shape = shape,
                modifier = modifier,
                onClick = { onAction(Action.FocusTimeoutClicked) },
            )
        },
        SettingsRow(key = "volume_key_action") { shape, modifier ->
            SettingsClickableItem(
                title = stringResource(R.string.camera_settings_volume_key_action),
                summary = stringResource(uiState.volumeKeyAction.selected.label),
                icon = Icons.Outlined.TouchApp,
                isChanged = uiState.volumeKeyAction.isChanged,
                shape = shape,
                modifier = modifier,
                onClick = { onAction(Action.VolumeKeyActionClicked) },
            )
        },
        SettingsRow(key = "timer_light") { shape, modifier ->
            SwitchSettingItem(
                setting = uiState.timerLight,
                title = stringResource(R.string.camera_settings_timer_light),
                summary = stringResource(R.string.camera_settings_timer_light_summary),
                label = stringResource(R.string.camera_settings_photo_only),
                icon = Icons.Outlined.Highlight,
                shape = shape,
                modifier = modifier,
                onCheckedChange = { onAction(Action.TimerLightToggled(it)) },
            )
        },
    )
}

private fun storageRows(
    uiState: UiState,
    onAction: (Action) -> Unit,
): List<SettingsRow> {
    val storageLocation = uiState.storageLocation ?: return emptyList()
    val storageSpace = uiState.storageSpace

    return listOfNotNull(
        storageSpace?.let { space ->
            SettingsRow(key = "storage_space") { shape, modifier ->
                SettingsStorageCard(
                    title = storageSpaceTitle(space),
                    summary = storageSpaceEstimate(space),
                    usedFraction = space.usedFraction,
                    shape = shape,
                    modifier = modifier,
                )
            }
        },
        SettingsRow(key = "storage_location") { shape, modifier ->
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
                modifier = modifier.animateContentSize(animationSpec = SETTLE_SIZE_SPEC),
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

private fun aboutRows(
    uiState: UiState,
    onAction: (Action) -> Unit,
): List<SettingsRow> {
    if (!uiState.showsLicenses) return emptyList()

    return listOf(
        SettingsRow(key = "licenses") { shape, modifier ->
            SettingsClickableItem(
                title = stringResource(R.string.camera_settings_licenses),
                summary = stringResource(R.string.camera_settings_licenses_summary),
                icon = Icons.Outlined.Description,
                shape = shape,
                modifier = modifier,
                onClick = { onAction(Action.LicensesClicked) },
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = FOOTER_TOP_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick) {
            Text(text = stringResource(R.string.camera_settings_reset_all_settings))
        }
    }
}

@Composable
private fun SwitchSettingItem(
    setting: SwitchSettingUiModel,
    title: String,
    summary: String,
    icon: ImageVector,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    onLabelClick: (() -> Unit)? = null,
) {
    SettingsSwitchItem(
        title = title,
        checked = setting.checked,
        onCheckedChange = onCheckedChange,
        summary = summary,
        label = label,
        onLabelClick = onLabelClick,
        icon = icon,
        enabled = setting.enabled,
        isChanged = setting.isChanged,
        shape = shape,
        modifier = modifier,
    )
}

private class SettingsRow(
    val key: String,
    val content: @Composable (shape: Shape, modifier: Modifier) -> Unit,
)
