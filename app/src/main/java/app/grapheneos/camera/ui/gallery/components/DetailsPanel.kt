package app.grapheneos.camera.ui.gallery.components

import android.util.Size as AndroidSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Velocity
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
    progress: () -> Float,
    details: GalleryDetails,
    isVideo: Boolean,
    uri: String,
    onShownChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isShown by remember(detailsState, progress) {
        derivedStateOf { progress() > 0f || detailsState.isDragging }
    }
    val currentOnShownChange by rememberUpdatedState(onShownChange)
    val scrollConnection = remember(detailsState) {
        DetailsPanelScrollConnection(
            detailsState = detailsState,
            onShownChange = { isShown -> currentOnShownChange(isShown) },
        )
    }
    val panelHeight = with(LocalDensity.current) { detailsState.panelHeight().toDp() }

    if (isShown) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(panelHeight)
                .graphicsLayer { translationY = detailsState.panelShift(progress()) }
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
                BottomSheetDefaults.DragHandle(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                DetailsPanelContent(
                    details = details,
                    isVideo = isVideo,
                    uri = uri,
                )
            }
        }
    }
}

@Composable
internal fun animateDetailsProgressAsState(
    detailsState: DetailsPanelState,
): State<Float> {
    val progress = remember(detailsState) { Animatable(detailsState.progress) }

    LaunchedEffect(progress) {
        progress.follow(
            target = { detailsState.progress },
            isAnimated = { !detailsState.isDragging },
            animationSpec = CONTROLS_SPEC,
        )
    }

    return progress.asState()
}

@Composable
private fun DetailsPanelContent(
    details: GalleryDetails,
    isVideo: Boolean,
    uri: String,
) {
    when (details) {
        is GalleryDetails.Loading -> Unit

        is GalleryDetails.Loaded -> FileDetails(
            details = details.details,
            isVideo = isVideo,
            uri = uri,
        )

        is GalleryDetails.Unavailable -> Text(
            text = stringResource(R.string.unable_to_obtain_file_details),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(PLACEHOLDER_PADDING),
        )
    }
}

private class DetailsPanelScrollConnection(
    private val detailsState: DetailsPanelState,
    private val onShownChange: (Boolean) -> Unit,
) : NestedScrollConnection {

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return when {
            detailsState.isDragging -> drag(available)
            else -> Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return when {
            source == NestedScrollSource.UserInput && available.y != 0f -> {
                detailsState.startDrag()
                drag(available)
            }
            else -> Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        return when {
            detailsState.isDragging -> {
                onShownChange(detailsState.release(available.y))
                available
            }
            else -> Velocity.Zero
        }
    }

    private fun drag(available: Offset): Offset {
        detailsState.drag(available.y)

        return Offset(
            x = 0f,
            y = available.y,
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
                progress = { 1f },
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
                onShownChange = {},
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
