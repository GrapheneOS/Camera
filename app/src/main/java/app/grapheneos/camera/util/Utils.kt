package app.grapheneos.camera.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.R
import app.grapheneos.camera.capturer.DEFAULT_MEDIA_STORE_CAPTURE_PATH
import app.grapheneos.camera.capturer.SAF_URI_HOST_EXTERNAL_STORAGE
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

fun getTreeDocumentUri(treeUri: Uri): Uri {
    val treeId = DocumentsContract.getTreeDocumentId(treeUri)
    return DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId)
}

fun ExecutorService.executeIfAlive(r: Runnable) {
    try {
        execute(r)
    } catch (ignored: RejectedExecutionException) {
        check(this.isShutdown)
    }
}

fun storageLocationToUiString(ctx: Context, sl: String): String {
    if (sl == CamConfig.SettingValues.Default.STORAGE_LOCATION) {
        return "${ctx.getString(R.string.main_storage)}/$DEFAULT_MEDIA_STORE_CAPTURE_PATH"
    }

    val uri = Uri.parse(sl)
    val indexOfId = if (DocumentsContract.isDocumentUri(ctx, uri)) 3 else 1
    val locationId = uri.pathSegments[indexOfId]

    if (uri.host == SAF_URI_HOST_EXTERNAL_STORAGE) {
        val endOfVolumeId = locationId.lastIndexOf(':')
        val volumeId = locationId.substring(0, endOfVolumeId)

        val volumeName = if (volumeId == "primary") {
            ctx.getString(R.string.main_storage)
        } else {
            val sm = ctx.getSystemService(StorageManager::class.java)
            sm.storageVolumes.find {
                volumeId == it.uuid
            }?.getDescription(ctx) ?: volumeId
        }

        val path = locationId.substring(endOfVolumeId + 1)

        return "$volumeName/$path"
    }

    try {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))

        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        ctx.contentResolver.query(docUri, projection, null, null)?.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
    } catch (ignored: Exception) {}

    return locationId
}

@Throws(IOException::class)
fun removePendingFlagFromUri(contentResolver: ContentResolver, uri: Uri) {
    val cv = ContentValues()
    cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
    if (contentResolver.update(uri, cv, null, null) != 1) {
        throw IOException("unable to remove IS_PENDING flag")
    }
}
