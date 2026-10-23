package app.grapheneos.camera.ui.mediaviewer.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.common.components.topBar
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme

private val BAR_HORIZONTAL_PADDING = 2.dp
private val BAR_VERTICAL_PADDING = 6.dp

@Composable
internal fun ViewerTopBar(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    navigation: @Composable RowScope.() -> Unit = {},
    title: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.topBar)
                .padding(
                    horizontal = BAR_HORIZONTAL_PADDING,
                    vertical = BAR_VERTICAL_PADDING,
                ),
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                content = navigation,
            )
            Box(modifier = Modifier.align(Alignment.Center)) {
                title()
            }
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                content = actions,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ViewerTopBarPreview() {
    CameraPreviewTheme {
        ViewerTopBar(
            navigation = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            },
        )
    }
}
