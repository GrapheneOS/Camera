package app.grapheneos.camera.data.media.store

internal fun interface StorageVolumeNames {
    fun name(uuid: String): String?
}
