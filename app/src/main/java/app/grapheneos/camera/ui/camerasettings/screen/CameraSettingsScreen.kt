package app.grapheneos.camera.ui.camerasettings.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
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
import app.grapheneos.camera.ui.camerasettings.components.SettingsTopAppBar
import app.grapheneos.camera.ui.camerasettings.components.rememberSettingsTopAppBarNestedScroll
import app.grapheneos.camera.ui.camerasettings.components.rememberSettingsTopAppBarScrollBehavior
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsAction as Action
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsUiState as UiState
import app.grapheneos.camera.ui.camerasettings.screen.model.StorageLocationUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.SwitchSettingUiModel
import app.grapheneos.camera.ui.core.CameraPreviewTheme
import app.grapheneos.camera.ui.core.CollectEvents

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

        cameraSettingsSections(
            uiState = uiState,
            onAction = onAction,
        )
    }
}

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
                canResetSettings = true,
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
