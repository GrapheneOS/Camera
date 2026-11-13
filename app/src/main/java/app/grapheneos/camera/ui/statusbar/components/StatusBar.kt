package app.grapheneos.camera.ui.statusbar.components

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
import app.grapheneos.camera.ui.quicksettings.navigation.QuickSetting
import app.grapheneos.camera.ui.statusbar.screen.model.StatusItemUiModel
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun StatusBar(
    items: ImmutableList<StatusItemUiModel>,
    enabled: Boolean,
    onItemClick: (QuickSetting) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var cutout by remember { mutableStateOf(Rect.Zero) }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Layout(
            content = {
                items.forEach { item ->
                    StatusItem(
                        item = item,
                        enabled = enabled,
                        onClick = onItemClick,
                    )
                }
            },
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
