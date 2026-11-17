package app.grapheneos.camera.ui.camerasettings.screen.model

import app.grapheneos.camera.data.media.model.StorageFolder
import app.grapheneos.camera.data.media.model.StorageSpace

internal data class StorageState(
    val location: String,
    val folder: StorageFolder? = null,
    val space: StorageSpace? = null,
    val isSpaceLoaded: Boolean = false,
)
