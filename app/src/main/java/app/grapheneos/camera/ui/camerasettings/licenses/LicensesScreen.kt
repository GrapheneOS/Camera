package app.grapheneos.camera.ui.camerasettings.licenses

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.grapheneos.camera.ui.camerasettings.components.rememberSettingsTopAppBarScrollBehavior
import app.grapheneos.camera.ui.camerasettings.licenses.components.LibrariesPage
import app.grapheneos.camera.ui.camerasettings.licenses.components.LicensePage
import app.grapheneos.camera.ui.camerasettings.licenses.model.LicensesAction as Action
import app.grapheneos.camera.ui.camerasettings.licenses.model.LicensesUiState as UiState
import app.grapheneos.camera.ui.components.motion.crossfade
import app.grapheneos.camera.ui.core.CollectEvents

@Composable
internal fun LicensesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    screenModel: LicensesScreenModel = hiltViewModel<LicensesViewModel>(),
) {
    val uiState by screenModel.uiState.collectAsStateWithLifecycle()
    val effectHandler = rememberLicensesEffectHandler()

    CollectEvents(
        events = screenModel.effects,
        onEvent = effectHandler::handle,
    )

    LicensesContent(
        uiState = uiState,
        onAction = screenModel::onAction,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicensesContent(
    uiState: UiState,
    onAction: (Action) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val listScrollBehavior = rememberSettingsTopAppBarScrollBehavior()

    BackHandler(enabled = uiState.openLicense != null) {
        onAction(Action.LicenseClosed)
    }

    AnimatedContent(
        targetState = uiState.openLicense,
        modifier = modifier,
        contentKey = { it?.coordinates },
        transitionSpec = { crossfade() },
        label = "license",
    ) { license ->
        when (license) {
            null -> LibrariesPage(
                libraries = uiState.libraries,
                listState = listState,
                scrollBehavior = listScrollBehavior,
                onLibraryClick = { onAction(Action.LibraryClicked(it.coordinates)) },
                onNavigateBack = onNavigateBack,
            )

            else -> LicensePage(
                license = license,
                onUrlClick = { onAction(Action.UrlClicked(it)) },
                onNavigateBack = { onAction(Action.LicenseClosed) },
            )
        }
    }
}
