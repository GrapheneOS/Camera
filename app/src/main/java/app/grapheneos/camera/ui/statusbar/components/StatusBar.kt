package app.grapheneos.camera.ui.statusbar.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.quicksettings.navigation.QuickSetting
import app.grapheneos.camera.ui.statusbar.screen.model.StatusItemUiModel
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun StatusBar(
    items: ImmutableList<StatusItemUiModel>,
    enabled: Boolean,
    onItemClick: (QuickSetting) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var cutout by remember { mutableStateOf(Rect.Zero) }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Layout(
            contents = listOf(
                {
                    items.forEach { item ->
                        StatusItem(
                            item = item,
                            enabled = enabled,
                            onClick = onItemClick,
                        )
                    }
                },
                {
                    StatusBarButton(
                        description = stringResource(R.string.camera_settings),
                        enabled = enabled,
                        onClick = onSettingsClick,
                    ) {
                        StatusIcon(icon = Icons.Outlined.Settings)
                    }
                },
            ),
            modifier = modifier.onPlaced { coordinates ->
                cutout = topCutout(
                    view = view,
                    coordinates = coordinates,
                )
            },
            measurePolicy = remember(cutout) { StatusBarMeasurePolicy(cutout = cutout) },
        )
    }
}
