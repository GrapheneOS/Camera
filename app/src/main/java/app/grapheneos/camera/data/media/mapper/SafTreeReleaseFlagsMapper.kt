package app.grapheneos.camera.data.media.mapper

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import javax.inject.Inject

interface SafTreeReleaseFlagsMapper {

    fun map(
        uri: Uri,
        isRead: Boolean,
        isWrite: Boolean,
        tracked: Collection<Uri>,
    ): Int
}

internal class SafTreeReleaseFlagsMapperImpl @Inject constructor() : SafTreeReleaseFlagsMapper {

    override fun map(
        uri: Uri,
        isRead: Boolean,
        isWrite: Boolean,
        tracked: Collection<Uri>,
    ): Int {
        // Storage locations are the only tree grants this app takes; anything else persisted here
        // belongs to a different feature and is none of our business.
        val isReleasable = DocumentsContract.isTreeUri(uri) && uri !in tracked

        return when {
            isReleasable -> flagsOf(
                isRead = isRead,
                isWrite = isWrite,
            )

            else -> 0
        }
    }

    private fun flagsOf(
        isRead: Boolean,
        isWrite: Boolean,
    ): Int {
        val read = when {
            isRead -> Intent.FLAG_GRANT_READ_URI_PERMISSION
            else -> 0
        }
        val write = when {
            isWrite -> Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            else -> 0
        }

        return read or write
    }
}
