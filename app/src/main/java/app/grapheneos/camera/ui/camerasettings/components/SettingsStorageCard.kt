package app.grapheneos.camera.ui.camerasettings.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.animation.SETTLE_SPEC
import app.grapheneos.camera.ui.core.animation.crossfadeResizing
import app.grapheneos.camera.ui.core.preview.CameraPreviewColumn

private const val TITLE_PLACEHOLDER_WIDTH = 0.4f
private const val SUMMARY_PLACEHOLDER_WIDTH = 0.7f
private const val PLACEHOLDER_MIN_ALPHA = 0.4f
private const val PLACEHOLDER_PULSE_MILLIS = 800

private val SUMMARY_SPACING = 4.dp
private val BAR_SPACING = 16.dp

@Composable
internal fun SettingsStorageCard(
    title: String?,
    usedFraction: Float?,
    modifier: Modifier = Modifier,
    summary: String? = null,
    shape: Shape = MaterialTheme.shapes.small,
) {
    val progress by animateFloatAsState(
        targetValue = usedFraction ?: 0f,
        animationSpec = SETTLE_SPEC,
        label = "settings_storage_card_progress",
    )

    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .fillMaxWidth()
            .padding(
                start = ITEM_START_PADDING,
                top = ITEM_VERTICAL_PADDING,
                end = ITEM_END_PADDING,
                bottom = ITEM_VERTICAL_PADDING,
            ),
    ) {
        AnimatedContent(
            targetState = title,
            transitionSpec = { crossfadeResizing() },
            label = "settings_storage_card_text",
        ) { shownTitle ->
            when (shownTitle) {
                null -> StoragePlaceholder()

                else -> StorageText(
                    title = shownTitle,
                    summary = summary,
                )
            }
        }
        Spacer(modifier = Modifier.height(BAR_SPACING))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StorageText(
    title: String,
    summary: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SUMMARY_SPACING)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StoragePlaceholder() {
    val pulse by rememberInfiniteTransition(label = "settings_storage_card_pulse").animateFloat(
        initialValue = 1f,
        targetValue = PLACEHOLDER_MIN_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PLACEHOLDER_PULSE_MILLIS),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "settings_storage_card_pulse_alpha",
    )

    Column(
        modifier = Modifier.graphicsLayer { alpha = pulse },
        verticalArrangement = Arrangement.spacedBy(SUMMARY_SPACING),
    ) {
        PlaceholderLine(
            style = MaterialTheme.typography.headlineSmall,
            widthFraction = TITLE_PLACEHOLDER_WIDTH,
        )
        PlaceholderLine(
            style = MaterialTheme.typography.bodyMedium,
            widthFraction = SUMMARY_PLACEHOLDER_WIDTH,
        )
    }
}

@Composable
private fun PlaceholderLine(
    style: TextStyle,
    widthFraction: Float,
) {
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }

    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(lineHeight)
            .clip(ShapeDefaults.ExtraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

@PreviewLightDark
@Composable
private fun SettingsStorageCardPreview() {
    CameraPreviewColumn {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsStorageCard(
                title = "23.68 GB free",
                summary = "About 3,036 photos or 143 minutes of video",
                usedFraction = 0.8f,
            )
            SettingsStorageCard(
                title = null,
                usedFraction = null,
            )
        }
    }
}
