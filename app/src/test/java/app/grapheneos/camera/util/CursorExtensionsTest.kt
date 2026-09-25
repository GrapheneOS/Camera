package app.grapheneos.camera.util

import android.database.MatrixCursor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CursorExtensionsTest {

    @Test
    fun columns_areReadByNameWhateverTheirPosition() {
        val cursor = cursorOf(
            columns = arrayOf(OTHER, NAME, SIZE),
            row = arrayOf("document-id", "IMG_1.jpg", 42L),
        )

        assertEquals("IMG_1.jpg", cursor.getStringOrNull(NAME))
        assertEquals(42L, cursor.getLongOrNull(SIZE))
    }

    @Test
    fun anAbsentColumn_readsAsNotSet() {
        val cursor = cursorOf(
            columns = arrayOf(OTHER),
            row = arrayOf("document-id"),
        )

        assertNull(cursor.getStringOrNull(NAME))
        assertNull(cursor.getLongOrNull(SIZE))
    }

    @Test
    fun aNullValue_readsAsNotSet() {
        val cursor = cursorOf(
            columns = arrayOf(NAME, SIZE),
            row = arrayOf(null, null),
        )

        assertNull(cursor.getStringOrNull(NAME))
        assertNull(cursor.getLongOrNull(SIZE))
    }

    private fun cursorOf(
        columns: Array<String>,
        row: Array<Any?>,
    ): MatrixCursor {
        val cursor = MatrixCursor(columns)
        cursor.addRow(row)
        cursor.moveToFirst()

        return cursor
    }

    private companion object {
        const val NAME = "_display_name"
        const val SIZE = "_size"
        const val OTHER = "document_id"
    }
}
