package app.grapheneos.camera

import android.content.ContentResolver
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import androidx.test.platform.app.InstrumentationRegistry
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.store.videoCollectionUri

/** Pending entries are how in-flight and orphaned outputs appear in MediaStore. */
fun pendingVideoCount(): Int {
    val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    val args = Bundle().apply {
        putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
        putString(
            ContentResolver.QUERY_ARG_SQL_SELECTION,
            "${MediaColumns.IS_PENDING} = 1" +
                " AND ${MediaColumns.DISPLAY_NAME} LIKE '${CapturedItemType.VIDEO.namePrefix}%'",
        )
    }
    return resolver.query(videoCollectionUri, arrayOf(MediaColumns._ID), args, null)
        ?.use { it.count } ?: 0
}
