package app.grapheneos.camera.ui.gallery.components.gesture

import android.view.ViewConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
internal fun rememberViewerGestures(
    transformState: ViewerTransformState,
    detailsState: DetailsPanelState,
    onDetailsShownChange: (Boolean) -> Unit,
): ViewerGestures {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val currentOnDetailsShownChange by rememberUpdatedState(onDetailsShownChange)

    return remember(
        transformState,
        detailsState,
        context,
        hapticFeedback,
    ) {
        ViewerGestures(
            transformState = transformState,
            detailsState = detailsState,
            minScalingSpan = ViewConfiguration.get(context).scaledMinimumScalingSpan.toFloat(),
            hapticFeedback = hapticFeedback,
            onDetailsShownChange = { isShown -> currentOnDetailsShownChange(isShown) },
        )
    }
}

@Stable
internal class ViewerGestures(
    val transformState: ViewerTransformState,
    val detailsState: DetailsPanelState,
    val minScalingSpan: Float,
    val hapticFeedback: HapticFeedback,
    val onDetailsShownChange: (Boolean) -> Unit,
)
