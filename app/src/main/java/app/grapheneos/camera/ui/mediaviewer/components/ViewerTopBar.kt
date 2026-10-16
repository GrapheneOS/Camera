package app.grapheneos.camera.ui.mediaviewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.CameraPreviewTheme
import app.grapheneos.camera.ui.core.cameraColors

private val BAR_HORIZONTAL_PADDING = 4.dp
private val BAR_VERTICAL_PADDING = 8.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ViewerTopBar(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.cameraColors
    val scrim = remember(colors.overlayScrim) {
        Brush.verticalGradient(listOf(colors.overlayScrim, Color.Transparent))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(scrim)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .padding(
                horizontal = BAR_HORIZONTAL_PADDING,
                vertical = BAR_VERTICAL_PADDING,
            ),
    ) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = colors.overlay,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ViewerTopBarPreview() {
    CameraPreviewTheme {
        ViewerTopBar(onNavigateBack = {})
    }
}
