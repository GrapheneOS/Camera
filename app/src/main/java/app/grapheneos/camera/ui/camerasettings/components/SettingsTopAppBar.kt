package app.grapheneos.camera.ui.camerasettings.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.core.CameraPreviewTheme

private val NAVIGATION_ICON_START_PADDING = 20.dp
private val TITLE_START_PADDING = 8.dp
private val TITLE_END_PADDING = 24.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTopAppBar(
    title: String,
    onNavigateBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    LargeTopAppBar(
        title = {
            SettingsTopAppBarTitle(title = title)
        },
        navigationIcon = {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.padding(start = NAVIGATION_ICON_START_PADDING),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun SettingsTopAppBarTitle(title: String) {
    val collapsedRowFontSize = MaterialTheme.typography.titleLarge.fontSize
    val isCollapsedRow = LocalTextStyle.current.fontSize == collapsedRowFontSize

    Text(
        text = title,
        modifier = Modifier.padding(
            start = TITLE_START_PADDING,
            end = TITLE_END_PADDING,
        ),
        overflow = TextOverflow.Ellipsis,
        maxLines = when {
            isCollapsedRow -> 1
            else -> Int.MAX_VALUE
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberSettingsTopAppBarScrollBehavior(): TopAppBarScrollBehavior {
    val collapsedOffset = with(LocalDensity.current) {
        (TopAppBarDefaults.LargeAppBarCollapsedHeight - TopAppBarDefaults.LargeAppBarExpandedHeight)
            .toPx()
    }
    val state = rememberTopAppBarState(
        initialHeightOffsetLimit = collapsedOffset,
        initialHeightOffset = collapsedOffset,
    )

    LaunchedEffect(state) {
        withFrameNanos {}
        state.heightOffset = state.heightOffsetLimit
    }

    return TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state = state)
}

// M3 also expands the bar with what is left of a fling; the system settings expand it only on a pull.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberSettingsTopAppBarNestedScroll(
    scrollBehavior: TopAppBarScrollBehavior,
): NestedScrollConnection {
    return remember(scrollBehavior) {
        val delegate = scrollBehavior.nestedScrollConnection

        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return delegate.onPreScroll(available, source)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                return when {
                    source == NestedScrollSource.SideEffect && available.y > 0f -> Offset.Zero
                    else -> delegate.onPostScroll(consumed, available, source)
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return delegate.onPreFling(available)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return delegate.onPostFling(consumed, available.copy(y = minOf(available.y, 0f)))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun SettingsTopAppBarPreview() {
    CameraPreviewTheme {
        SettingsTopAppBar(
            title = "Settings",
            onNavigateBack = {},
            scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
        )
    }
}
