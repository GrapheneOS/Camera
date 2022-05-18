package app.grapheneos.camera.util

import android.content.Context
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.R
import app.grapheneos.camera.capturer.DEFAULT_MEDIA_STORE_CAPTURE_PATH
import app.grapheneos.camera.capturer.SAF_URI_HOST_EXTERNAL_STORAGE
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
