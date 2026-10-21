package app.grapheneos.camera.ui.gallery.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme
import app.grapheneos.camera.ui.mediaviewer.components.ViewerTopBar

private val BUTTON_SIZE = 44.dp
private val BUTTON_START_PADDING = 8.dp

@Composable
internal fun DetailsBackButton(
    progress: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isShown by remember(progress) { derivedStateOf { progress() > 0f } }

    if (isShown) {
        ViewerTopBar(
            modifier = modifier.graphicsLayer {
                alpha = progress()
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
            containerColor = Color.Transparent,
            navigation = {
                FilledIconButton(
                    onClick = onClick,
                    modifier = Modifier
                        .padding(start = BUTTON_START_PADDING)
                        .minimumInteractiveComponentSize()
                        .size(BUTTON_SIZE),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun DetailsBackButtonPreview() {
    CameraPreviewTheme {
        DetailsBackButton(
            progress = { 1f },
            onClick = {},
        )
    }
}
