package app.grapheneos.camera.ui.gallery.screen

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import app.grapheneos.camera.ui.gallery.components.details.DetailsPanelState
import app.grapheneos.camera.ui.gallery.components.pager.gesture.ViewerTransformState
import app.grapheneos.camera.ui.gallery.screen.model.GalleryAction as Action

@Composable
internal fun GalleryViewerEffects(
    pageKey: String?,
    transformState: ViewerTransformState,
    detailsState: DetailsPanelState,
    showsDetails: Boolean,
    onAction: (Action) -> Unit,
) {
    val currentOnAction by rememberUpdatedState(onAction)

    LaunchedEffect(pageKey, transformState) {
        if (pageKey == null) return@LaunchedEffect

        snapshotFlow { transformState.isZoomed() }
            .collect { isZoomed ->
                currentOnAction(
                    Action.ZoomChanged(
                        pageKey = pageKey,
                        isZoomed = isZoomed,
                    ),
                )
            }
    }

    LaunchedEffect(detailsState, showsDetails) {
        detailsState.show(showsDetails)
    }

    BackHandler(enabled = showsDetails) {
        currentOnAction(Action.DetailsShownChanged(isShown = false))
    }
}
