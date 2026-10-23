package app.grapheneos.camera.ui.gallery.components.details

import android.util.Size as AndroidSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import app.grapheneos.camera.data.media.model.CapturedItemDetails
import app.grapheneos.camera.data.media.model.MediaTimestamp
import app.grapheneos.camera.ui.common.components.systemBarsAndCutout
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.gallery.screen.model.GalleryDetails
import java.time.LocalDateTime

private val PREVIEW_WIDTH = 360.dp
private val PREVIEW_HEIGHT = 720.dp

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
        val isSidePanel = detailsState.isSidePanel()

        Surface(
            modifier = modifier
                .panelBounds(detailsState)
                .offset { detailsState.panelOffsetAt(position()).round() }
                .nestedScroll(scrollConnection),
            shape = panelShape(isSidePanel),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(panelInsets(isSidePanel)),
            ) {
                DetailsPanelContent(
                    details = details,
                    isVideo = isVideo,
                    uri = uri,
                    showsDragHandle = !isSidePanel,
                    dragHandleAlpha = { 1f - detailsState.expansionAt(position()) },
                    onOpenMapClick = onOpenMapClick,
                    onCopy = onCopy,
                    modifier = Modifier.onSizeChanged { size ->
                        contentHeight.intValue = size.height
                    },
                )
            }
        }
    }
}

@Composable
private fun Modifier.panelBounds(detailsState: DetailsPanelState): Modifier {
    val panelWidth = with(LocalDensity.current) { detailsState.panelWidth().toDp() }

    return when {
        detailsState.isSidePanel() -> fillMaxHeight().width(panelWidth)
        else -> fillMaxSize()
    }
}

@Composable
private fun panelShape(isSidePanel: Boolean): Shape {
    val shape = MaterialTheme.shapes.extraLarge

    return when {
        isSidePanel -> shape.copy(
            topEnd = ZeroCornerSize,
            bottomEnd = ZeroCornerSize,
        )
        else -> shape.copy(
            bottomStart = ZeroCornerSize,
            bottomEnd = ZeroCornerSize,
        )
    }
}

@Composable
private fun panelInsets(isSidePanel: Boolean): WindowInsets {
    val sides = when {
        isSidePanel -> WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom
        else -> WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
    }

    return WindowInsets.systemBarsAndCutout(sides)
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
                    sidePanelWidth = 0f,
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
