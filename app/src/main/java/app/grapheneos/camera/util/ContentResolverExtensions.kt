package app.grapheneos.camera.util

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.util.Log
import app.grapheneos.camera.BuildConfig

private const val TAG = "ContentResolverExtensions"

fun <T> ContentResolver.queryFirstRow(
    uri: Uri,
    projection: Array<String>,
    read: (Cursor) -> T?,
): T? {
    return query(uri, projection, null, null)?.use { cursor ->
        cursor.takeIf { it.moveToFirst() }?.let(read)
    }
}

@Suppress("TooGenericExceptionCaught")
fun <T> ContentResolver.queryFirstRowOrNull(
    uri: Uri,
    projection: Array<String>,
    read: (Cursor) -> T?,
): T? {
    return try {
        queryFirstRow(uri, projection, read)
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "unable to query ${projection.toList()} of $uri", e)
        }
        null
    }
}
