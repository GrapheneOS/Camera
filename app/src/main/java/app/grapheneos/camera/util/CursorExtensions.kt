package app.grapheneos.camera.util

import android.database.Cursor
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull

/**
 * A provider is free to ignore the projection it was handed and answer with its own columns --
 * the DocumentsProvider contract explicitly allows it -- so reading by position can hand back an
 * unrelated column, e.g. a document id that then renders as a 1970 date. These look the column
 * up by name instead and treat "absent" the same as "not set".
 */
fun Cursor.getStringOrNull(columnName: String): String? {
    return getColumnIndex(columnName)
        .takeIf { index -> index >= 0 }
        ?.let { index -> getStringOrNull(index) }
}

/** See [getStringOrNull] for why the column is looked up by name. */
fun Cursor.getLongOrNull(columnName: String): Long? {
    return getColumnIndex(columnName)
        .takeIf { index -> index >= 0 }
        ?.let { index -> getLongOrNull(index) }
}
