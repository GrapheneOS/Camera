package app.grapheneos.camera.ui.camerasettings.licenses.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.camerasettings.components.GROUPED_ITEM_SPACING
import app.grapheneos.camera.ui.camerasettings.components.SCREEN_CONTENT_PADDING
import app.grapheneos.camera.ui.camerasettings.components.SettingsClickableItem
import app.grapheneos.camera.ui.camerasettings.components.SettingsTopAppBar
import app.grapheneos.camera.ui.camerasettings.components.groupedItemShape
import app.grapheneos.camera.ui.camerasettings.components.rememberSettingsTopAppBarNestedScroll
import app.grapheneos.camera.ui.camerasettings.components.rememberSettingsTopAppBarScrollBehavior
import app.grapheneos.camera.ui.camerasettings.licenses.model.LibraryUiModel
import app.grapheneos.camera.ui.core.CameraPreviewTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibrariesPage(
    libraries: ImmutableList<LibraryUiModel>,
    listState: LazyListState,
    scrollBehavior: TopAppBarScrollBehavior,
    onLibraryClick: (LibraryUiModel) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topAppBarNestedScroll = rememberSettingsTopAppBarNestedScroll(scrollBehavior)

    Scaffold(
        modifier = modifier.nestedScroll(topAppBarNestedScroll),
        topBar = {
            SettingsTopAppBar(
                title = stringResource(R.string.camera_settings_licenses),
                onNavigateBack = onNavigateBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding + PaddingValues(all = SCREEN_CONTENT_PADDING),
            verticalArrangement = Arrangement.spacedBy(GROUPED_ITEM_SPACING),
        ) {
            itemsIndexed(
                items = libraries,
                key = { _, library -> library.coordinates },
            ) { index, library ->
                SettingsClickableItem(
                    title = library.name,
                    summary = library.coordinates,
                    shape = groupedItemShape(
                        index = index,
                        count = libraries.size,
                    ),
                    onClick = { onLibraryClick(library) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun LibrariesPagePreview() {
    CameraPreviewTheme {
        LibrariesPage(
            libraries = persistentListOf(
                LibraryUiModel(
                    name = "Camera Core",
                    coordinates = "androidx.camera:camera-core:1.6.2",
                ),
                LibraryUiModel(
                    name = "ZXing Core",
                    coordinates = "com.google.zxing:core:3.5.4",
                ),
            ),
            listState = rememberLazyListState(),
            scrollBehavior = rememberSettingsTopAppBarScrollBehavior(),
            onLibraryClick = {},
            onNavigateBack = {},
        )
    }
}
