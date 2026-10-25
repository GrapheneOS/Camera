package app.grapheneos.camera.ui.gallery.components.pager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalLayoutDirection
import app.grapheneos.camera.ui.gallery.components.pager.gesture.detectVideoTaps
import app.grapheneos.camera.ui.mediaviewer.playback.SkipDirection

@Composable
internal fun VideoTapArea(
    onTap: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onSkip: (SkipDirection, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnSkip by rememberUpdatedState(onSkip)
    val placement = remember { Placement() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPlaced { coordinates -> placement.coordinates = coordinates }
            .pointerInput(layoutDirection) {
                detectVideoTaps(
                    layoutDirection = layoutDirection,
                    screenPositionOf = placement::screenPositionOf,
                    screenWidth = placement::screenWidth,
                    onTap = { currentOnTap() },
                    onDoubleTap = { position -> currentOnDoubleTap(position) },
                    onSkip = { direction, position -> currentOnSkip(direction, position) },
                )
            },
    )
}

private class Placement {

    var coordinates: LayoutCoordinates? = null

    fun screenPositionOf(position: Offset): Offset {
        return coordinates?.localToRoot(position) ?: position
    }

    fun screenWidth(): Float {
        return coordinates?.findRootCoordinates()?.size?.width?.toFloat() ?: 0f
    }
}
