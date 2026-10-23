package app.grapheneos.camera.ui.gallery.components.details

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.model.CapturedItemDetails
import app.grapheneos.camera.ui.common.components.copyOnLongClick

private const val FILE_NAME_MAX_LINES = 2

private val SEGMENT_SPACING = 2.dp
private val SEGMENT_PADDING = 20.dp
private val SEGMENT_VERTICAL_PADDING = 16.dp
private val HEADER_VERTICAL_PADDING = 8.dp
private val EXPOSURE_VERTICAL_PADDING = 20.dp
private val ICON_TEXT_SPACING = 20.dp
private val CHIP_SPACING = 8.dp
private val CHIP_HORIZONTAL_PADDING = 8.dp
private val CHIP_VERTICAL_PADDING = 2.dp
private val LINE_SPACING = 4.dp
private val CHEVRON_PADDING = 12.dp
private val SETTING_DIVIDER_HEIGHT = 8.dp

@Composable
internal fun FileDetailsCard(
    details: CapturedItemDetails,
    isVideo: Boolean,
    uri: String,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCopy: (String) -> Unit,
) {
    val camera = details.camera
    val cameraName = camera?.let { cameraName(it) }
    val settings = camera?.let { exposureSettings(it) }.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(SEGMENT_SPACING)) {
        HeaderSegment(
            copyText = cameraName ?: details.fileName,
            isExpanded = isExpanded,
            onExpandedChange = onExpandedChange,
            onCopy = onCopy,
            shape = segmentShape(
                isFirst = true,
                isLast = !isExpanded,
            ),
        ) {
            when (cameraName) {
                null -> FileNameAndFacts(
                    details = details,
                    isVideo = isVideo,
                    nameColor = MaterialTheme.colorScheme.onSurface,
                )
                else -> Text(
                    text = cameraName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        AnimatedVisibility(visible = isExpanded) {
            ExpandedSegments(
                details = details,
                isVideo = isVideo,
                uri = uri,
                settings = settings,
                showsFile = cameraName != null,
                onCopy = onCopy,
            )
        }
    }
}

@Composable
private fun ExpandedSegments(
    details: CapturedItemDetails,
    isVideo: Boolean,
    uri: String,
    settings: List<String>,
    showsFile: Boolean,
    onCopy: (String) -> Unit,
) {
    val copyLabel = stringResource(R.string.copy_to_clipboard)
    val middleShape = segmentShape(
        isFirst = false,
        isLast = false,
    )

    Column(verticalArrangement = Arrangement.spacedBy(SEGMENT_SPACING)) {
        if (settings.isNotEmpty()) {
            ExposureSegment(
                settings = settings,
                shape = middleShape,
                onCopy = onCopy,
            )
        }
        if (showsFile) {
            Segment(shape = middleShape) {
                FileNameAndFacts(
                    details = details,
                    isVideo = isVideo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .copyOnLongClick(
                            text = details.fileName,
                            copyLabel = copyLabel,
                            onCopy = onCopy,
                        )
                        .padding(
                            horizontal = SEGMENT_PADDING,
                            vertical = SEGMENT_VERTICAL_PADDING,
                        ),
                )
            }
        }
        StorageSegment(
            details = details,
            uri = uri,
            shape = segmentShape(
                isFirst = false,
                isLast = true,
            ),
            onCopy = onCopy,
        )
    }
}

@Composable
private fun HeaderSegment(
    copyText: String,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCopy: (String) -> Unit,
    shape: CornerBasedShape,
    title: @Composable () -> Unit,
) {
    Segment(shape = shape) {
        Row(
            modifier = Modifier
                .copyOnLongClick(
                    text = copyText,
                    copyLabel = stringResource(R.string.copy_to_clipboard),
                    onCopy = onCopy,
                    onClickLabel = stringResource(
                        when {
                            isExpanded -> R.string.gallery_details_show_less
                            else -> R.string.gallery_details_show_more
                        },
                    ),
                    onClick = { onExpandedChange(!isExpanded) },
                )
                .padding(
                    start = SEGMENT_PADDING,
                    top = HEADER_VERTICAL_PADDING,
                    bottom = HEADER_VERTICAL_PADDING,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Camera,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(ICON_TEXT_SPACING))
            Column(modifier = Modifier.weight(1f)) {
                title()
            }
            Icon(
                imageVector = when {
                    isExpanded -> Icons.Outlined.KeyboardArrowUp
                    else -> Icons.Outlined.KeyboardArrowDown
                },
                contentDescription = null,
                modifier = Modifier.padding(CHEVRON_PADDING),
            )
        }
    }
}

@Composable
private fun FileNameAndFacts(
    details: CapturedItemDetails,
    isVideo: Boolean,
    modifier: Modifier = Modifier,
    nameColor: Color = Color.Unspecified,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LINE_SPACING),
    ) {
        Text(
            text = details.fileName,
            style = MaterialTheme.typography.bodyLarge,
            color = nameColor,
            maxLines = FILE_NAME_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        FactChips(
            facts = fileFacts(
                details = details,
                isVideo = isVideo,
            ),
        )
    }
}

@Composable
private fun FactChips(facts: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(CHIP_SPACING),
        verticalArrangement = Arrangement.spacedBy(CHIP_SPACING),
    ) {
        facts.forEach { fact ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = fact,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(
                        horizontal = CHIP_HORIZONTAL_PADDING,
                        vertical = CHIP_VERTICAL_PADDING,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ExposureSegment(
    settings: List<String>,
    shape: CornerBasedShape,
    onCopy: (String) -> Unit,
) {
    Segment(shape = shape) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .copyOnLongClick(
                    text = settings.joinToString(separator = " "),
                    copyLabel = stringResource(R.string.copy_to_clipboard),
                    onCopy = onCopy,
                )
                .padding(
                    horizontal = SEGMENT_PADDING,
                    vertical = EXPOSURE_VERTICAL_PADDING,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            settings.forEachIndexed { index, setting ->
                if (index > 0) {
                    VerticalDivider(modifier = Modifier.height(SETTING_DIVIDER_HEIGHT))
                }
                Text(
                    text = setting,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun StorageSegment(
    details: CapturedItemDetails,
    uri: String,
    shape: CornerBasedShape,
    onCopy: (String) -> Unit,
) {
    val context = LocalContext.current
    val size = remember(context, details.sizeBytes) {
        details.sizeBytes?.let { Formatter.formatFileSize(context, it) }
    }
    val folder = remember(context, uri, details) {
        folder(
            context = context,
            uri = uri,
            details = details,
        )
    }
    val lines = listOfNotNull(folder, modifiedText(details))

    Segment(shape = shape) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .copyOnLongClick(
                    text = listOfNotNull(size).plus(lines).joinToString(separator = "\n"),
                    copyLabel = stringResource(R.string.copy_to_clipboard),
                    onCopy = onCopy,
                )
                .padding(
                    horizontal = SEGMENT_PADDING,
                    vertical = SEGMENT_VERTICAL_PADDING,
                ),
            verticalArrangement = Arrangement.spacedBy(LINE_SPACING),
        ) {
            size?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            lines.forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun Segment(
    shape: CornerBasedShape,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
private fun segmentShape(
    isFirst: Boolean,
    isLast: Boolean,
): CornerBasedShape {
    val outer = MaterialTheme.shapes.large
    val inner = MaterialTheme.shapes.extraSmall
    val top = when {
        isFirst -> outer
        else -> inner
    }
    val bottom = when {
        isLast -> outer
        else -> inner
    }

    return outer.copy(
        topStart = top.topStart,
        topEnd = top.topEnd,
        bottomEnd = bottom.bottomEnd,
        bottomStart = bottom.bottomStart,
    )
}
