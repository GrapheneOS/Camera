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
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.ShutterSpeed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.VideoCameraFront
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import app.grapheneos.camera.ui.camerasettings.components.formatPhotoQuality
import app.grapheneos.camera.ui.camerasettings.components.groupedItemShape
import app.grapheneos.camera.ui.camerasettings.components.rememberSettingsTopAppBarNestedScroll
import app.grapheneos.camera.ui.camerasettings.components.rememberSettingsTopAppBarScrollBehavior
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsAction as Action
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsUiState as UiState
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoQualityUiModel
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
    val storageLocationPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { tree ->
        screenModel.onAction(Action.StorageLocationPicked(tree))
    }
    val effectHandler = rememberCameraSettingsEffectHandler(
        storageLocationPicker = storageLocationPicker,
    )

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
        SettingsRow(key = "save_image_as_previewed") { shape ->
            SwitchSettingItem(
                setting = uiState.saveImageAsPreviewed,
                title = stringResource(R.string.save_image_as_previewed),
                summary = stringResource(R.string.save_as_image_preview_desc),
                label = stringResource(R.string.camera_settings_photo_only),
                icon = Icons.Outlined.CameraFront,
                shape = shape,
                onCheckedChange = { onAction(Action.SaveImageAsPreviewedToggled(it)) },
            )
        },
        SettingsRow(key = "save_video_as_previewed") { shape ->
            SwitchSettingItem(
                setting = uiState.saveVideoAsPreviewed,
                title = stringResource(R.string.save_video_as_previewed),
                summary = stringResource(R.string.save_as_video_preview_desc),
                label = stringResource(R.string.camera_settings_video_only),
                icon = Icons.Outlined.VideoCameraFront,
                shape = shape,
                onCheckedChange = { onAction(Action.SaveVideoAsPreviewedToggled(it)) },
            )
        },
    )
}

private fun qualityRows(
    uiState: UiState,
    onAction: (Action) -> Unit,
): List<SettingsRow> {
    val photoQuality = SettingsRow(key = "photo_quality") { shape ->
        SettingsClickableItem(
            title = stringResource(R.string.image_quality),
            summary = photoQualitySummary(uiState.photoQuality),
            label = stringResource(R.string.camera_settings_photo_only),
            icon = Icons.Outlined.HighQuality,
            isChanged = uiState.photoQuality.isChanged,
            shape = shape,
            onClick = { onAction(Action.PhotoQualityClicked) },
        )
    }
    val highestResolution = SettingsRow(key = "highest_resolution") { shape ->
        SwitchSettingItem(
            setting = uiState.selectHighestResolution,
            title = stringResource(R.string.highest_res_setting),
            summary = stringResource(R.string.highest_res_setting_desc),
            label = stringResource(R.string.camera_settings_photo_only),
            icon = Icons.Outlined.PhotoSizeSelectLarge,
            shape = shape,
            onCheckedChange = { onAction(Action.HighestResolutionToggled(it)) },
        )
    }
    val zsl = uiState.zsl?.let { setting ->
        SettingsRow(key = "zsl") { shape ->
            SwitchSettingItem(
                setting = setting,
                title = stringResource(R.string.zsl_setting_title),
                summary = stringResource(R.string.zsl_setting_desc),
                label = stringResource(R.string.camera_settings_photo_only),
                icon = Icons.Outlined.ShutterSpeed,
                shape = shape,
                onCheckedChange = { onAction(Action.ZslToggled(it)) },
            )
        }
    }

    return listOfNotNull(
        photoQuality,
        highestResolution,
        zsl,
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
    val label = when (val level = photoQuality.level) {
        null -> R.string.camera_settings_photo_quality_custom
        else -> level.label
    }

    return stringResource(
        R.string.camera_settings_photo_quality_summary,
        stringResource(label),
        formatPhotoQuality(photoQuality.quality),
    )
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
                saveImageAsPreviewed = SwitchSettingUiModel(checked = true),
                saveVideoAsPreviewed = SwitchSettingUiModel(checked = true),
                removeExif = SwitchSettingUiModel(checked = true),
                zsl = SwitchSettingUiModel(),
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
