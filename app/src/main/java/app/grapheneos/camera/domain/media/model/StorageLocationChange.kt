package app.grapheneos.camera.domain.media.model

sealed interface StorageLocationChange {

    data class Changed(
        val location: String,
    ) : StorageLocationChange

    data object Rejected : StorageLocationChange
}
