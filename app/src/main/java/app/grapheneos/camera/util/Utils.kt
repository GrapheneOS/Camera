package app.grapheneos.camera.util

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.util.Locale

fun Throwable.printStackTraceToString(): String {
    val baos = ByteArrayOutputStream(1000)
    this.printStackTrace(PrintStream(baos))
    return baos.toString()
}

/** Video length as mm:ss, widening to hh:mm:ss only once there is an hour to show. */
fun formatVideoDuration(totalSeconds: Long): String {
    val seconds = totalSeconds % 60
    val minutes = totalSeconds / 60 % 60
    val hours = totalSeconds / 3600

    return if (hours == 0L) {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    }
}

fun getTreeDocumentUri(treeUri: Uri): Uri {
    val treeId = DocumentsContract.getTreeDocumentId(treeUri)
    return DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId)
}

@Throws(IOException::class)
fun removePendingFlagFromUri(contentResolver: ContentResolver, uri: Uri) {
    val cv = ContentValues()
    cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
    if (contentResolver.update(uri, cv, null, null) != 1) {
        throw IOException("unable to remove IS_PENDING flag")
    }
}
