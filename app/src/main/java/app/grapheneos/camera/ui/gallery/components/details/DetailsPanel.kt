package app.grapheneos.camera.ui.gallery.components.details

import android.util.Size as AndroidSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.model.CapturedItemDetails
import app.grapheneos.camera.data.media.model.MediaTimestamp
import app.grapheneos.camera.ui.core.animation.CONTROLS_SPEC
import app.grapheneos.camera.ui.core.animation.follow
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.gallery.components.gesture.DetailsPanelState
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDetails
import java.time.LocalDateTime

private val PLACEHOLDER_PADDING = 24.dp
private val PREVIEW_WIDTH = 360.dp
private val PREVIEW_HEIGHT = 720.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailsPanel(
    detailsState: DetailsPanelState,
    position: () -> Float,
    details: GalleryDetails,
    isVideo: Boolean,
    uri: String,
    isCurrentPage: Boolean,
    onShownChange: (Boolean) -> Unit,
    onOpenMapClick: (() -> Unit)?,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentHeight = remember { mutableIntStateOf(0) }
    val currentOnShownChange by rememberUpdatedState(onShownChange)
    val isShown by remember(detailsState, position) {
        derivedStateOf {
            detailsState.isDragging || (position() > 0f && detailsState.isLaidOut())
        }
    }
    val scrollConnection = remember(detailsState) {
        DetailsPanelScrollConnection(
            detailsState = detailsState,
            onShownChange = { isShown -> currentOnShownChange(isShown) },
        )
    }

    LaunchedEffect(detailsState, isCurrentPage) {
        if (isCurrentPage) {
            snapshotFlow { contentHeight.intValue }
                .collect { height -> detailsState.updateContentHeight(height.toFloat()) }
        }
    }

    if (isShown) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer { translationY = detailsState.topAt(position()) }
                .nestedScroll(scrollConnection),
            shape = MaterialTheme.shapes.extraLarge.copy(
                bottomStart = ZeroCornerSize,
                bottomEnd = ZeroCornerSize,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier.onSizeChanged { size ->
                        contentHeight.intValue = size.height
                    },
                ) {
                    BottomSheetDefaults.DragHandle(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .graphicsLayer {
                                alpha = 1f - detailsState.expansionAt(position())
                            },
                    )
                    DetailsPanelContent(
                        details = details,
                        isVideo = isVideo,
                        uri = uri,
                        onOpenMapClick = onOpenMapClick,
                        onCopy = onCopy,
                    )
                }
            }
        }
    }
}

@Composable
internal fun animateDetailsPositionAsState(
    detailsState: DetailsPanelState,
): State<Float> {
    val position = remember(detailsState) { Animatable(detailsState.targetPosition()) }

    LaunchedEffect(position) {
        position.follow(
            target = detailsState::targetPosition,
            isAnimated = { !detailsState.isDragging },
            animationSpec = CONTROLS_SPEC,
        )
    }

    return position.asState()
}

@Composable
private fun DetailsPanelContent(
    details: GalleryDetails,
    isVideo: Boolean,
    uri: String,
    onOpenMapClick: (() -> Unit)?,
    onCopy: (String) -> Unit,
) {
    when (details) {
        is GalleryDetails.Loading -> Unit

        is GalleryDetails.Loaded -> FileDetails(
            details = details.details,
            isVideo = isVideo,
            uri = uri,
            onOpenMapClick = onOpenMapClick,
            onCopy = onCopy,
        )

        is GalleryDetails.Unavailable -> Text(
            text = stringResource(R.string.unable_to_obtain_file_details),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(PLACEHOLDER_PADDING),
        )
    }
}

@PreviewLightDark
@Composable
private fun DetailsPanelPreview() {
    val dateTime = LocalDateTime.of(2023, 10, 16, 12, 0)

    CameraPreviewTheme {
        BoxWithConstraints(
            modifier = Modifier.size(
                width = PREVIEW_WIDTH,
                height = PREVIEW_HEIGHT,
            ),
        ) {
            val containerSize = Size(
                width = constraints.maxWidth.toFloat(),
                height = constraints.maxHeight.toFloat(),
            )
            val detailsState = remember(containerSize) {
                DetailsPanelState(
                    cornerRadius = 0f,
                    settleVelocity = 0f,
                    isShown = true,
                ).apply { updateLayout(containerSize) }
            }

            DetailsPanel(
                detailsState = detailsState,
                position = { 1f },
                details = GalleryDetails.Loaded(
                    CapturedItemDetails(
                        fileName = "IMG_20231016_120000_000.jpg",
                        relativePath = "DCIM/Camera/",
                        sizeBytes = 3_456_789,
                        resolution = AndroidSize(4032, 3024),
                        duration = null,
                        created = MediaTimestamp.WallClock(dateTime),
                        modified = null,
                    ),
                ),
                isVideo = false,
                uri = "content://media/external/images/media/1",
                isCurrentPage = true,
                onShownChange = {},
                onOpenMapClick = {},
                onCopy = {},
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
