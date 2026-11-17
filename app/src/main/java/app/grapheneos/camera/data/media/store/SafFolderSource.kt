package app.grapheneos.camera.data.media.store

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import app.grapheneos.camera.data.media.model.SAF_URI_HOST_EXTERNAL_STORAGE
import app.grapheneos.camera.data.media.model.StorageFolder
import app.grapheneos.camera.util.getStringOrNull
import app.grapheneos.camera.util.queryFirstRowOrNull
import javax.inject.Inject

internal interface SafFolderSource {

    /** The folder of a document tree or of a document in one; null for any other [uri]. */
    fun folder(uri: Uri): StorageFolder?
}

internal class SafFolderSourceImpl @Inject constructor(
    private val contentResolver: ContentResolver,
    private val storageVolumeNames: StorageVolumeNames,
) : SafFolderSource {

    override fun folder(uri: Uri): StorageFolder? {
        return when {
            !DocumentsContract.isTreeUri(uri) -> null

            uri.authority == SAF_URI_HOST_EXTERNAL_STORAGE -> {
                externalStorageFolder(DocumentsContract.getTreeDocumentId(uri))
            }

            else -> treeFolder(uri)
        }
    }

    // ExternalStorageProvider names a document "<volume>:<path>"; only the path may hold a ':'.
    private fun externalStorageFolder(documentId: String): StorageFolder {
        val volumeId = documentId.substringBefore(VOLUME_SEPARATOR, missingDelimiterValue = "")
        val path = documentId.substringAfter(VOLUME_SEPARATOR)

        return when (volumeId) {
            "" -> StorageFolder.Document(name = documentId)
            PRIMARY_VOLUME_ID -> StorageFolder.MainStorage(path = path)
            else -> StorageFolder.Volume(
                name = storageVolumeNames.name(volumeId) ?: volumeId,
                path = path,
            )
        }
    }

    private fun treeFolder(uri: Uri): StorageFolder {
        val treeId = DocumentsContract.getTreeDocumentId(uri)
        val column = DocumentsContract.Document.COLUMN_DISPLAY_NAME
        val name = contentResolver.queryFirstRowOrNull(
            uri = DocumentsContract.buildDocumentUriUsingTree(uri, treeId),
            projection = arrayOf(column),
        ) { it.getStringOrNull(column) }

        return StorageFolder.Document(name = name ?: treeId)
    }

    private companion object {
        const val VOLUME_SEPARATOR = ':'
        const val PRIMARY_VOLUME_ID = "primary"
    }
}
