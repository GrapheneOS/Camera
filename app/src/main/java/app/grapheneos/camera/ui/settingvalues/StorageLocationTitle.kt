package app.grapheneos.camera.ui.settingvalues

import android.content.Context
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.core.net.toUri
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.model.MEDIA_STORE_CAPTURE_PATH
import app.grapheneos.camera.data.media.model.SAF_URI_HOST_EXTERNAL_STORAGE
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.util.getStringOrNull
import app.grapheneos.camera.util.queryFirstRow

fun storageLocationTitle(context: Context, location: String): String {
    if (location == CapturedItemRepository.MEDIA_STORE_LOCATION) {
        return "${context.getString(R.string.main_storage)}/$MEDIA_STORE_CAPTURE_PATH"
    }

    val uri = location.toUri()
    val indexOfId = if (DocumentsContract.isDocumentUri(context, uri)) 3 else 1
    val locationId = uri.pathSegments[indexOfId]

    return when (uri.host) {
        SAF_URI_HOST_EXTERNAL_STORAGE -> externalStorageTitle(context, locationId)
        else -> documentName(context, uri) ?: locationId
    }
}

private fun externalStorageTitle(context: Context, locationId: String): String {
    val endOfVolumeId = locationId.lastIndexOf(':')
    val volumeId = locationId.substring(0, endOfVolumeId)

    val volumeName = if (volumeId == "primary") {
        context.getString(R.string.main_storage)
    } else {
        val storageManager = context.getSystemService(StorageManager::class.java)
        storageManager.storageVolumes.find {
            volumeId == it.uuid
        }?.getDescription(context) ?: volumeId
    }

    val path = locationId.substring(endOfVolumeId + 1)

    return "$volumeName/$path"
}

private fun documentName(context: Context, treeUri: Uri): String? {
    return try {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )

        val column = DocumentsContract.Document.COLUMN_DISPLAY_NAME
        context.contentResolver.queryFirstRow(
            uri = documentUri,
            projection = arrayOf(column),
        ) { it.getStringOrNull(column) }
    } catch (ignored: Exception) {
        null
    }
}
