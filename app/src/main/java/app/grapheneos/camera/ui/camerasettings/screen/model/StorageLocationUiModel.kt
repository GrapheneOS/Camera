package app.grapheneos.camera.ui.camerasettings.screen.model

import androidx.compose.runtime.Immutable

@Immutable
internal data class StorageLocationUiModel(
    val location: String,
    val isChanged: Boolean,
)
