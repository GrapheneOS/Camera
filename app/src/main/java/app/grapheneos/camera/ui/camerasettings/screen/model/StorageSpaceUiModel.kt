package app.grapheneos.camera.ui.camerasettings.screen.model

import androidx.compose.runtime.Immutable

@Immutable
internal data class StorageSpaceUiModel(
    val freeBytes: Long? = null,
    val usedFraction: Float? = null,
    val photos: Long? = null,
    val videoMinutes: Long? = null,
)
