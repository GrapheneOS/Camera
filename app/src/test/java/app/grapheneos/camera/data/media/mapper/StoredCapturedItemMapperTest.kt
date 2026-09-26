package app.grapheneos.camera.data.media.mapper

import android.net.Uri
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.store.StoredCapturedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoredCapturedItemMapperTest {

    private val mapper = StoredCapturedItemMapperImpl()

    @Test
    fun anItem_isStoredWithTheShippedTypeValues() {
        assertEquals(
            StoredCapturedItem.TYPE_IMAGE,
            mapper.map(item(CapturedItemType.IMAGE)).type,
        )
        assertEquals(
            StoredCapturedItem.TYPE_VIDEO,
            mapper.map(item(CapturedItemType.VIDEO)).type,
        )
    }

    @Test
    fun aStoredItem_readsBackAsTheItemThatWasStored() {
        CapturedItemType.entries.forEach { type ->
            val item = item(type)
            val reloaded = mapper.map(mapper.map(item))

            assertEquals(item, reloaded)
            assertEquals(type, reloaded?.type)
            assertEquals(item.uri, reloaded?.uri)
        }
    }

    @Test
    fun aStoredTypeThisVersionDoesNotKnow_readsAsNoItem() {
        val stored = StoredCapturedItem(
            type = -1,
            dateString = DATE_STRING,
            uri = URI.toString(),
        )

        assertNull(mapper.map(stored))
    }

    private fun item(type: CapturedItemType): CapturedItem {
        return CapturedItem(
            type = type,
            dateString = DATE_STRING,
            uri = URI,
        )
    }

    private companion object {
        const val DATE_STRING = "20260926_120000_000"

        val URI: Uri = Uri.parse("content://media/external/images/media/1")
    }
}
