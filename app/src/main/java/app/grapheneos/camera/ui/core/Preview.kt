package app.grapheneos.camera.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal const val PREVIEW_ASPECT_RATIO = 3f / 4f
internal const val PREVIEW_TALL_ASPECT_RATIO = 9f / 16f

@Composable
internal fun CameraPreviewTheme(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CameraTheme(darkTheme = darkTheme) {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}

@Composable
internal fun CameraPreviewColumn(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CameraPreviewTheme(
        modifier = modifier,
        darkTheme = darkTheme,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(all = 16.dp),
        ) {
            content()
        }
    }
}

@Composable
internal fun CameraPreviewViewfinder(
    modifier: Modifier = Modifier,
    aspectRatio: Float = PREVIEW_ASPECT_RATIO,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio = aspectRatio)
            .background(
                color = PREVIEW_SCENE,
                shape = MaterialTheme.shapes.large,
            ),
        content = content,
    )
}

@Composable
internal fun CameraPreviewSample(
    status: String,
    controls: @Composable FlowRowScope.() -> Unit,
    viewfinderAspectRatio: Float = PREVIEW_ASPECT_RATIO,
    viewfinder: @Composable BoxScope.() -> Unit,
) {
    CameraPreviewColumn {
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
            )
            CameraPreviewViewfinder(
                aspectRatio = viewfinderAspectRatio,
                content = viewfinder,
            )
            FlowRow(content = controls)
        }
    }
}

@Composable
internal fun CameraPreviewControl(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(text = text)
    }
}
