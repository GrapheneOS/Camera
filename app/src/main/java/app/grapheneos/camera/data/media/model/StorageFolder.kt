package app.grapheneos.camera.data.media.model

sealed interface StorageFolder {

    data class MainStorage(
        val path: String,
    ) : StorageFolder

    data class Volume(
        val name: String,
        val path: String,
    ) : StorageFolder

    data class Document(
        val name: String,
    ) : StorageFolder
}
