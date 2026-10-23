package app.grapheneos.camera.ui.camerasettings.licenses.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.camerasettings.components.GROUPED_ITEM_SPACING
import app.grapheneos.camera.ui.camerasettings.components.SCREEN_CONTENT_PADDING
import app.grapheneos.camera.ui.camerasettings.components.SettingsTopAppBar
import app.grapheneos.camera.ui.camerasettings.components.linkedText
import app.grapheneos.camera.ui.camerasettings.components.rememberSettingsTopAppBarNestedScroll
import app.grapheneos.camera.ui.camerasettings.components.rememberSettingsTopAppBarScrollBehavior
import app.grapheneos.camera.ui.camerasettings.licenses.model.LicenseUiModel
import app.grapheneos.camera.ui.core.preview.CameraPreviewTheme

private val HEADER_HORIZONTAL_PADDING = 8.dp
private val HEADER_TOP_PADDING = 24.dp
private val HEADER_BOTTOM_PADDING = 8.dp
private val TEXT_PADDING = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LicensePage(
    license: LicenseUiModel,
    onUrlClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = rememberSettingsTopAppBarScrollBehavior()
    val topAppBarNestedScroll = rememberSettingsTopAppBarNestedScroll(scrollBehavior)

    Scaffold(
        modifier = modifier.nestedScroll(topAppBarNestedScroll),
        topBar = {
            SettingsTopAppBar(
                title = license.name,
                onNavigateBack = onNavigateBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(
                start = SCREEN_CONTENT_PADDING,
                end = SCREEN_CONTENT_PADDING,
                bottom = SCREEN_CONTENT_PADDING,
            ),
            verticalArrangement = Arrangement.spacedBy(GROUPED_ITEM_SPACING),
        ) {
            item(key = "header") {
                LicenseHeader(
                    license = license,
                    onUrlClick = onUrlClick,
                )
            }

            item(key = "text") {
                LicenseText(text = license.text)
            }
        }
    }
}

@Composable
private fun LicenseHeader(
    license: LicenseUiModel,
    onUrlClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = HEADER_HORIZONTAL_PADDING,
                end = HEADER_HORIZONTAL_PADDING,
                top = HEADER_TOP_PADDING,
                bottom = HEADER_BOTTOM_PADDING,
            ),
    ) {
        Text(
            text = license.coordinates,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        license.url?.let { url ->
            Text(
                text = linkedText(
                    text = url,
                    link = url,
                    onClick = { onUrlClick(url) },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun LicenseText(text: String) {
    SelectionContainer {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(TEXT_PADDING),
        )
    }
}

@PreviewLightDark
@Composable
private fun LicensePagePreview() {
    CameraPreviewTheme {
        LicensePage(
            license = LicenseUiModel(
                name = "Guava InternalFutureFailureAccess and InternalFutures",
                coordinates = "com.google.guava:failureaccess:1.0.2",
                text = "Apache License\nVersion 2.0, January 2004",
                url = "https://github.com/google/guava",
            ),
            onUrlClick = {},
            onNavigateBack = {},
        )
    }
}
