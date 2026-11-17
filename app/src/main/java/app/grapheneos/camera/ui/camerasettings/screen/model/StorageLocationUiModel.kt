package app.grapheneos.camera.ui.camerasettings.screen.model

import androidx.compose.runtime.Immutable
import app.grapheneos.camera.data.media.model.StorageFolder

@Immutable
internal data class StorageLocationUiModel(
    val folder: StorageFolder?,
    val isChanged: Boolean,
)
