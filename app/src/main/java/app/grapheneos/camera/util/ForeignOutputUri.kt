package app.grapheneos.camera.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log

private const val TAG = "ForeignOutputUri"

/**
 * Whether an output uri handed in through [MediaStore.EXTRA_OUTPUT] may be written to.
 *
 * The capture activities are exported, and they write the uri with this app's own access, not the
 * caller's. Nothing guarantees the caller granted it: the grant only gets attached when the caller
 * doesn't set its own ClipData, and MediaProvider passes the grant check for any uri anyway. So a
 * caller with no access of its own could point the write at whatever this app alone can reach: the
 * user's photos and videos it took, everything under the storage location the user picked, and its
 * own private files. Refuse those and leave the caller with whatever it could write itself.
 *
 * Queries MediaProvider, so it must not run where a stalled one can't be waited on.
 */
fun isForeignOutputUriAllowed(ctx: Context, uri: Uri): Boolean {
    // A file uri is opened directly, with this app's file access
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
        return false
    }

    // "<userId>@authority" reaches the same provider and grants as the bare authority, which the
    // checks below compare by
    val authority = uri.authority
    if (authority == null || uri.userInfo != null || uri.port != -1) {
        return false
    }

    if (ctx.packageManager.resolveContentProvider(authority, 0)?.packageName == ctx.packageName) {
        return false
    }

    val persistedTrees = ctx.contentResolver.persistedUriPermissions.map { it.uri }
    if (isInsideAnyTree(uri, persistedTrees)) {
        return false
    }

    if (authority == MediaStore.AUTHORITY) {
        return !isOwnMedia(ctx, uri)
    }

    return true
}

// Split out so the decision can be tested: an app can't fake persisted grants
internal fun isInsideAnyTree(uri: Uri, trees: Collection<Uri>): Boolean {
    if (!DocumentsContract.isTreeUri(uri)) {
        return false
    }
    // A tree grant covers every uri below it, matched by decoded path segments
    val treeId = DocumentsContract.getTreeDocumentId(uri)
    return trees.any {
        DocumentsContract.isTreeUri(it) && it.authority == uri.authority &&
                DocumentsContract.getTreeDocumentId(it) == treeId
    }
}

// Media this app saved is writable by it as the owner, without any grant. Anything it can't look
// up is treated as its own, since it then can't be told apart.
private fun isOwnMedia(ctx: Context, uri: Uri): Boolean {
    val projection = arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
    return try {
        ctx.contentResolver.query(uri, projection, null, null)?.use {
            val index = it.getColumnIndex(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
            if (!it.moveToFirst() || index < 0) {
                return@use true
            }
            // Null for media no app owns, which only a grant can make writable
            it.getString(index) == ctx.packageName
        } ?: true
    } catch (e: Exception) {
        Log.d(TAG, "unable to look up the owner of $uri", e)
        true
    }
}
