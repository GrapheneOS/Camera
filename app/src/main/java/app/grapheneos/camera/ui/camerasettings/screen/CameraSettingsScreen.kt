package app.grapheneos.camera.ui.camerasettings.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.camerasettings.components.GROUPED_ITEM_SPACING
import app.grapheneos.camera.ui.camerasettings.components.KeepAtEndEffect
import app.grapheneos.camera.ui.camerasettings.components.SCREEN_CONTENT_PADDING
import app.grapheneos.camera.ui.camerasettings.components.SecureSessionBanner
import app.grapheneos.camera.ui.camerasettings.components.SettingsCategoryHeader
import app.grapheneos.camera.ui.camerasettings.components.SettingsClickableItem
import app.grapheneos.camera.ui.camerasettings.components.SettingsSwitchItem
import app.grapheneos.camera.ui.camerasettings.components.SettingsTopAppBar
import app.grapheneos.camera.ui.camerasettings.components.formatMegapixels
import app.grapheneos.camera.ui.camerasettings.components.formatPhotoQuality
import app.grapheneos.camera.ui.camerasettings.components.groupedItemShape
import app.grapheneos.camera.ui.camerasettings.components.rememberSettingsTopAppBarNestedScroll
import app.grapheneos.camera.ui.camerasettings.components.rememberSettingsTopAppBarScrollBehavior
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsAction as Action
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsUiState as UiState
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoQualityUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoResolutionUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.StorageLocationUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.SwitchSettingUiModel
import app.grapheneos.camera.ui.components.motion.SETTLE_SIZE_SPEC
import app.grapheneos.camera.ui.core.CameraPreviewTheme
import app.grapheneos.camera.ui.core.CollectEvents
import app.grapheneos.camera.util.storageLocationToUiString

@Composable
internal fun CameraSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    screenModel: CameraSettingsScreenModel = hiltViewModel<CameraSettingsViewModel>(),
) {
    val uiState by screenModel.uiState.collectAsStateWithLifecycle()
    val locationPermissionRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        screenModel.onAction(Action.LocationPermissionAnswered)
    }
    val storageLocationPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { tree ->
        screenModel.onAction(Action.StorageLocationPicked(tree))
    }
    val effectHandler = rememberCameraSettingsEffectHandler(
        locationPermissionRequest = locationPermissionRequest,
        storageLocationPicker = storageLocationPicker,
    )

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        screenModel.onAction(Action.ScreenResumed)
    }

    CollectEvents(
        events = screenModel.effects,
        onEvent = effectHandler::handle,
    )

    CameraSettingsContent(
        uiState = uiState,
        onAction = screenModel::onAction,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraSettingsContent(
    uiState: UiState,
    onAction: (Action) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = rememberSettingsTopAppBarScrollBehavior()
    val topAppBarNestedScroll = rememberSettingsTopAppBarNestedScroll(scrollBehavior)

    Scaffold(
        modifier = modifier.nestedScroll(topAppBarNestedScroll),
        topBar = {
            SettingsTopAppBar(
                title = stringResource(R.string.camera_settings),
                onNavigateBack = onNavigateBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        CameraSettingsList(
            uiState = uiState,
            onAction = onAction,
            contentPadding = contentPadding,
        )
    }

    CameraSettingsDialogs(
        uiState = uiState,
        onAction = onAction,
    )
}

@Composable
private fun CameraSettingsList(
    uiState: UiState,
    onAction: (Action) -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()

    KeepAtEndEffect(listState)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding + PaddingValues(
            start = SCREEN_CONTENT_PADDING,
            end = SCREEN_CONTENT_PADDING,
            bottom = SCREEN_CONTENT_PADDING,
        ),
        verticalArrangement = Arrangement.spacedBy(GROUPED_ITEM_SPACING),
    ) {
        if (uiState.showsSecureSessionBanner) {
            item(key = "secure_session_banner") {
                SecureSessionBanner(
                    text = stringResource(R.string.camera_settings_secure_session_banner),
                    modifier = Modifier.padding(top = SCREEN_CONTENT_PADDING),
                )
            }
        }

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

    return when (val resolution = photoQuality.resolution) {
        null -> stringResource(
            R.string.camera_settings_photo_quality_summary,
            compression,
            percent,
        )

        else -> stringResource(
            R.string.camera_settings_photo_quality_summary_with_resolution,
            megapixels(
                resolution = resolution,
                isFull = photoQuality.isFullResolution,
            ),
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

@PreviewLightDark
@Composable
private fun CameraSettingsContentPreview() {
    CameraPreviewTheme {
        CameraSettingsContent(
            uiState = UiState(
                gyroscopeSuggestions = SwitchSettingUiModel(
                    checked = true,
                    isChanged = true,
                ),
                keepSelfiesAsSeen = SwitchSettingUiModel(checked = true),
                removeExif = SwitchSettingUiModel(checked = true),
                cameraSounds = SwitchSettingUiModel(checked = true),
                storageLocation = StorageLocationUiModel(
                    location = "",
                    isChanged = false,
                ),
            ),
            onAction = {},
            onNavigateBack = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun CameraSettingsContentSecureSessionPreview() {
    CameraPreviewTheme {
        CameraSettingsContent(
            uiState = UiState(
                showsSecureSessionBanner = true,
                removeExif = SwitchSettingUiModel(
                    checked = true,
                    enabled = false,
                ),
                cameraSounds = SwitchSettingUiModel(checked = true),
            ),
            onAction = {},
            onNavigateBack = {},
        )
    }
}
